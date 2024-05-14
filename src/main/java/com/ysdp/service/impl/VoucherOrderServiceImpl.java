package com.ysdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.ysdp.dto.Result;
import com.ysdp.entity.VoucherOrder;
import com.ysdp.mapper.VoucherOrderMapper;
import com.ysdp.service.ISeckillVoucherService;
import com.ysdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ysdp.utils.RedisIdWork;
import com.ysdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWork redisIdWork;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    //redisson实现并发锁
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    //静态同步代码块，初始化与类一起加载
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    //创建线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct  //当前类初始化完成就开始执行
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    //创建一个线程任务内部类，此任务应该在项目启动时就开始，因为刚开始就可能有用户下单
    private class VoucherOrderHandler implements Runnable{
        String queueName = "stream.orders";
        @Override
        public void run() {
            while(true){

                try {
                    //1.获取消息队列中的订单信息   当队列中无元素时take方法会阻塞，不会消耗cpu
                    // xreadgroup group g1 c1 count 1 block 2000 streams streams.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息是否获取成功
                    if(list==null || list.size()==0){
                        //2.1获取失败，说明没消息继续下一次循环
                        continue;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //2.3获取成功可以下单
                    handleVoucherOrder(voucherOrder);
                    //3.ACK确认  sack streams.orders g1 c1
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());

                } catch (Exception e) {
                    //处理未确认的消息
                    log.error("处理消息未确认异常");
                    handlePendingList();
                }

            }
        }

        private void handlePendingList() {
            while(true){

                try {
                    //1.获取pending-list中的订单信息   当队列中无元素时take方法会阻塞，不会消耗cpu
                    // xreadgroup group g1 c1 count 1  streams streams.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //2.判断消息是否获取成功
                    if(list==null || list.size()==0){
                        //2.1获取失败，说明pending-list中没消息结束循环
                        break;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //2.3获取成功可以下单
                    handleVoucherOrder(voucherOrder);
                    //3.ACK确认  sack streams.orders g1 c1
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());

                } catch (Exception e) {
                    //处理未确认的消息
                   log.error("处理pend-list异常");
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }

            }
        }
    }

    //阻塞队列，当一个线程尝试向阻塞队列获取元素时，队列中无元素会被阻塞，只有队列中有元素时才会被唤醒
   /* private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    //创建一个线程任务内部类，此任务应该在项目启动时就开始，因为刚开始就可能有用户下单
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while(true){

                try {
                    //1.获取队列中的订单信息   当队列中无元素时take方法会阻塞，不会消耗cpu
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {

                }

            }
        }
    }*/

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户id
        Long userId = voucherOrder.getUserId();
        //使用redisson获取锁解决秒杀过程中的并发问题
        // 创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁                          等待重试时间 超时自动释放锁时间 时间单位
        //boolean isLock = lock.tryLock(1,10, TimeUnit.SECONDS);
        boolean isLock = lock.tryLock();//不指定参数为，获取锁失败直接返回
        //判断释放获取锁成功
        if(!isLock){
            //获取锁失败。返回错误或重试
            log.error("不允许重复下单");
            return;
        }
        try {

            proxy.creatVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
    }

    IVoucherOrderService proxy;
    /**
     * 优惠券秒杀功能
     * @param voucherId
     * @return
     */
    //将优惠券保存到redis中在redis中完成秒杀功能，并开启一个独立线程完成数据库插入操作实现异步下单 基于redis的stream消息队列实现
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //获取订单id
        long orderId = redisIdWork.nextId("order");
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        //2.判断结果是否为0
        int r = result.intValue();
        if(r!=0){
            //2.1 不为0没有购买资格
            return Result.fail(r==1 ? "库存不足" : "不能重复下单");
        }
        //3.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //4.返回订单id
        return Result.ok(orderId);

    }



    //将优惠券保存到redis中在redis中完成秒杀功能，并开启一个独立线程完成数据库插入操作实现异步下单 基于java阻塞队列实现
   /* @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        //2.判断结果是否为0
        int r = result.intValue();
        if(r!=0){
            //2.1 不为0没有购买资格
            return Result.fail(r==1 ? "库存不足" : "不能重复下单");
        }
       //2.2为0有购买资格
        long orderId = redisIdWork.nextId("order");
        //2.3.保存到阻塞队列中
        //2.4.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        //2.5 代金券id
        voucherOrder.setVoucherId(voucherId);
        //2.6 用户id
        voucherOrder.setUserId(userId);

        //2.7加入阻塞队列
            orderTasks.add(voucherOrder);
        //3.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //4.返回订单id
        return Result.ok(orderId);

    }*/





    //java代码操作数据库实现秒杀优惠券，因为涉及多次查询数据库所以高并发下性能不是很好
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //1.根据id查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀活动未开始!");
        }
        //3.判断秒杀是否结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀活动已过期");
        }
        //4.判断库存是否充足
        if(voucher.getStock()<1){
            return Result.fail("优惠券已售完");
        }


        Long userId = UserHolder.getUser().getId();

        //使用redisson获取锁解决秒杀过程中的并发问题
        // 创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁                          等待重试时间 超时自动释放锁时间 时间单位
        //boolean isLock = lock.tryLock(1,10, TimeUnit.SECONDS);
        boolean isLock = lock.tryLock();//不指定参数为，获取锁失败直接返回
        //判断释放获取锁成功
        if(!isLock){
            //获取锁失败。返回错误或重试
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }


        //自定义锁实现
       *//* // 创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        //获取锁
        boolean isLock = lock.tryLock(12000);
        //判断释放获取锁成功
        if(!isLock){
            //获取锁失败。返回错误或重试
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }*//*

    }*/

    //直接操作数据库判断秒杀下单
    //对数据库的更新和修改，开启事务完成
   /* @Transactional
    public  Result creatVoucherOrder(Long voucherId) {
        //5.实现一人一单
        Long userId = UserHolder.getUser().getId();


            //5.1查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            //5.2判断是否存在
            if(count>0){
                //用户已经购买过
                return Result.ok("不可重复下单！");
            }


            //6.扣减库存  乐观锁解决并发问题
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock -1")
                    .eq("voucher_id", voucherId)
                    .gt("stock",0)
                    .update();

            if(!success){
                return Result.fail("库存不足");
            }

            //7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            //7.1 订单id
            long orderId = redisIdWork.nextId("order");
            voucherOrder.setId(orderId);
            //7.2 代金券id
            voucherOrder.setVoucherId(voucherId);
            //7.3 用户id
            voucherOrder.setUserId(userId);

            save(voucherOrder);
            //8.返回订单

            return Result.ok(orderId);

    }*/
    //异步秒杀下单
    @Transactional
    public  void creatVoucherOrder(VoucherOrder voucherOrder) {
        //5.实现一人一单
        Long userId = voucherOrder.getUserId();

        long voucherId = voucherOrder.getVoucherId();

        //5.1查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //5.2判断是否存在
        if(count>0){
            //用户已经购买过
            log.error("不可重复下单");
        }


        //6.扣减库存  乐观锁解决并发问题
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();

        if(!success){
            log.error("库存不足");
        }

        //保存订单
        save(voucherOrder);
        //8.返回订单

    }
}
