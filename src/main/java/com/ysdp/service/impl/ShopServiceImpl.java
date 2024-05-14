package com.ysdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ysdp.dto.Result;
import com.ysdp.entity.Shop;
import com.ysdp.mapper.ShopMapper;
import com.ysdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ysdp.utils.CacheClient;
import com.ysdp.utils.RedisData;
import com.ysdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.ysdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    CacheClient cacheClient;

    /*//创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);*/
    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        // Shop shop = queryWithPassThrough(id);
         //使用自定义工具类实现

        //lambda queryWithMutex(CACHE_SHOP_KEY,id,Shop.class,id2->getById(id2),CACHE_SHOP_TTL,TimeUnit.SECONDS);

       /* Shop shop = cacheClient.
                queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);*/

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
       // Shop shop = queryWithLogicalExpire(id);

        Shop shop = cacheClient.
                queryWithLogicalExpire(CACHE_SHOP_KEY,id, Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.SECONDS);

        if(shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
   /* //逻辑过期解决缓存击穿
    private Shop queryWithLogicalExpire(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断redis中是否命中
        if(StrUtil.isBlank(shopJson)){
            //3.未命中，返回空
            return null;
        }
        //4.命中,先把json反序列化位对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData(); //未指定数据的类型，只能返回一个JsonObject对象
        //再次通过工具类转回Shop对象
        Shop shop = JSONUtil.toBean(data, Shop.class);
        //获取逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 6.未过期，返回数据
            return shop;
        }

        //7过期，尝试获取锁
        String lockKey = LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        //8判断获取锁是否成功
        if(isLock){
            //再次查询redis查看是否过期

            //1.从redis中查询商铺缓存
             shopJson = stringRedisTemplate.opsForValue().get(key);
            //4.命中,先把json反序列化位对象
             redisData = JSONUtil.toBean(shopJson, RedisData.class);
             data = (JSONObject) redisData.getData(); //未指定数据的类型，只能返回一个JsonObject对象
            //再次通过工具类转回Shop对象
             shop = JSONUtil.toBean(data, Shop.class);
            //获取逻辑过期时间
             expireTime = redisData.getExpireTime();
            //5.判断是否过期
            if(expireTime.isAfter(LocalDateTime.now())){
                // 6.未过期，返回数据
                return shop;
            }


            //获得锁成功，开启独立线程实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    saveShopToRedis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }


        //失败，返回过期数据
        //返回过期数据
        return shop;
    }*/
    //互斥锁解决缓存击穿
    private Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断redis中是否命中
        if(StrUtil.isNotBlank(shopJson)){
            //命中，返回数据
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断命中是否是空值
        if(shopJson!=null){
            //返回一个错误信息
            return null;
        }
        String lockKey = null;
        Shop shop = null;
        try {
            //3.实现缓存重建
            //3.1获取互斥锁
            lockKey = LOCK_SHOP_KEY+id;
            boolean isLock = tryLock(lockKey);
            //3.2判断是否成功
            if(!isLock){
                //3.3失败，休眠一段时间并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //4成功获取锁，再次查询redis中是否有缓存
            String checkRedis = stringRedisTemplate.opsForValue().get(key);
            //判断redis中是否命中
            if(StrUtil.isNotBlank(checkRedis)){
                //命中，返回数据
                 shop = JSONUtil.toBean(checkRedis, Shop.class);
                unlock(lockKey);
                return shop;
            }
            //未命中，根据id查询数据库
            shop = getById(id);
            Thread.sleep(200);
            //5.判断商铺是否存在
            if(shop==null){
                //不存在，将空值写入redis解决缓存穿透问题
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //6存在，将查寻到的数据写入redis中，返回商铺信息
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        //7释放互斥锁

        return shop;
    }

    //解决缓存穿透
    /*private Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断redis中是否命中
        if(StrUtil.isNotBlank(shopJson)){
            //命中，返回数据
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断命中是否是空值
        if(shopJson!=null){
            //返回一个错误信息
            return null;
        }

        //未命中，根据id查询数据库
        Shop shop = getById(id);
        //3.判断商铺是否存在
        if(shop==null){
            //不存在，将空值写入redis解决缓存穿透问题
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //存在，将查寻到的数据写入redis中，返回商铺信息
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }
    */
    //获取锁
    private boolean tryLock(String key){
        boolean flag =  stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        //直接返回flag会做自动拆箱，可能出现空指针，建议使用工具类
        return BooleanUtil.isTrue(flag);
    }
    //释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
    //模拟后台添加热点数据
    public void saveShopToRedis(Long id,Long expireSecond) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));
        //3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        this.updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * 根据商铺类型分页查询商铺信息 或根据地理坐标查询
     * @param typeId
     * @param current
     * @param x 经度
     * @param y 纬度
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.判断是否需要根据坐标查询
        if(x==null || y==null){
            Page<Shop> page = query().eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current  * SystemConstants.DEFAULT_PAGE_SIZE;
        //3 查询redis 按照距离排序 分页 （结果：shopId、distance）
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(key,
                        GeoReference.fromCoordinate(x, y),//指定某一经纬度为圆心
                        new Distance(5000),//指定半径 默认单位米                指定结果获取距离   查询范围 从0 - 参数
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        //4 解析出id
        if(results==null){
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if(list.size()<=from){
            //没有下一页了
            return Result.ok(Collections.emptyList());
        }
        //4.1截取 from - end 的部分
        List<Long> ids = new ArrayList<>(list.size());//保存指定范围内的店铺id集合
        Map<String,Distance> distanceMap = new HashMap<>(list.size());//保存对应店铺id和距离
        list.stream().skip(from).forEach(result->{
            //4.2获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            //4.3获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });
        //5. 根据id查询数据库
        String idStr = StrUtil.join(",",ids);
        List<Shop> shops = query().in("id", ids).last("order by field (id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //6. 返回
        //
        return Result.ok(shops);
    }
}
