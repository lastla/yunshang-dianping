package com.ysdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWork {
    //开始时间戳 2022 1 1 0 0
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    //序列号的位数
    private static final int COUNT_BITS = 32;


    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWork(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //生成id,64为 最高位0代表正数 后31位为时间戳 后32位为利用redis生成的自增id
    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //利用redis的自增生成32位序列号
        /**
         * key的选取，redis的自增最大为64位，要保证产生的序列号不会超过32位，并且还要能承受数千万的订单量
         * 这里采用为键拼一个当前日期，既满足自增，又能满足大订单量,将来还可方便统计某天的订单量
         */
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //返回的是long型不能直接拼接
        // 先对时间戳进行左移运算，再对时间戳和序列号进行或运算（效率高）
        return timestamp << COUNT_BITS | count;
    }
}
