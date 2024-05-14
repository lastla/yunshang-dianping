package com.ysdp;

import com.ysdp.entity.Shop;
import com.ysdp.service.impl.ShopServiceImpl;
import com.ysdp.utils.RedisIdWork;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class YunShangDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private RedisIdWork redisIdWork;

    @Resource
    StringRedisTemplate stringRedisTemplate;
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWork() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = ()->{
            for (int i = 0; i <100 ; i++) {
                long id = redisIdWork.nextId("order");
                System.out.println("id= "+id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i <300 ; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("end = "+ (end-begin));
    }


    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShopToRedis(1L,10L);
    }

    /**
     * 加载店铺坐标到redis中
     */
    @Test
    void loadShopData(){
        //1.查询店铺信息
        List<Shop> list = shopService.list();
        //2.将店铺分组，按照typeId分组，typeId一致的放到一个集合中
        //Map<Long,List<Shop>> map = list.stream().collect(Collectors.groupingBy(shop -> shop.getTypeId()));
        Map<Long,List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.分批写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1获取类型id
            Long typeId = entry.getKey();
            String key = "shop:geo:"+typeId;
            //3.2 获取同类型的店铺集合
            List<Shop> value = entry.getValue();
            //先创造一个location集合，并将经纬度和值存入，然后批量添加提升效率
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            //3.3写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
               // stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }

            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }

    /**
     * 测试百万数据HyperLOGLOG的内存使用情况
     */
    @Test
    void testHyperLogLog(){
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i <1000000 ; i++) {
            j =  i % 1000;
            values[j] = "user_"+i;
            if(j == 999){
                //发送到redis
                stringRedisTemplate.opsForHyperLogLog().add("hl2",values);
            }
        }

        // 统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println(count);

    }


}
