package com.feed01.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feed01.dto.Result;
import com.feed01.entity.SeckillVoucher;
import com.feed01.entity.VoucherOrder;
import com.feed01.mapper.VoucherOrderMapper;
import com.feed01.service.ISeckillVoucherService;
import com.feed01.service.IVoucherOrderService;
import com.feed01.utils.RedisIdWorker;
import com.feed01.utils.UserHolder;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.data.redis.connection.stream.StreamOffset;
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
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 当前类一初始化就来执行
    @PostConstruct
    private void init(){
        // 提交任务
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 内部类: 线程任务
    private class VoucherOrderHandler implements Runnable{
        String queueName = "stream.orders";
        String groupName = "g1";
        @Override
        public void run() {
            try {
                // 使用 opsForStream() 创建消费组，StreamOffset.fromStart() 等同于 "0"
                stringRedisTemplate.opsForStream().createGroup(queueName, groupName);
                log.info("消费组已创建。");
            } catch (Exception e) {
                // 如果消费组已存在，Spring 会抛出一个异常
                if (e.getMessage().contains("BUSYGROUP")) {
                    log.error("消费组已经存在，不需要创建。");
                } else {
                    log.error("发生其他错误: {}" ,e.getMessage());
                }
            }

            while (true){
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(groupName, "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2.判断消息获取是否成功
                    if(list == null || list.isEmpty()){
                    // 2.1.如果获取失败,说明没有消息,继续下一次循环
                        continue;
                    }
                    // 解析消息中的订单信息
                    MapRecord<String, Object, Object> entries = list.get(0);
                    Map<Object, Object> values = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

                    // 3.如果获取成功,可以下单
                    handleVoucherOrder(voucherOrder);
                    // 4.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName ,"g1",entries.getId());
                } catch (Exception e) {
                    log.error("处理订单异常" ,e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true){
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 2.判断消息获取是否成功
                    if(list == null || list.isEmpty()){
                        // 2.1.如果获取失败,说明pending-list中没有消息,,结束循环
                        break;
                    }
                    // 解析消息中的订单信息
                    MapRecord<String, Object, Object> entries = list.get(0);
                    Map<Object, Object> values = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

                    // 3.如果获取成功,可以下单
                    handleVoucherOrder(voucherOrder);
                    // 4.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName ,"g1",entries.getId());
                } catch (Exception e) {
                    log.error("处理pending-list订单异常" ,e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 获取用户
//        Long userId = UserHolder.getUser().getId(); // 因为现在是子线程 是拿不到ThreadLocal
        Long userId = voucherOrder.getUserId();
        // 创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = lock.tryLock();
        // 判断是否获取锁成功
        if(!isLock){
            log.error("不允许重复下单");
            return;
        }

        try {
            proxy.createVoucherOrder_Lua(voucherOrder);
        } finally {
            // 释放
            lock.unlock();
        }
    }

    // 设置成员变量就不用一直拿
    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher_Lua2(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        long orderId = redisIdWorker.nextId("order");

        Long execute = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString() ,String.valueOf(orderId));

        int i = Objects.requireNonNull(execute).intValue();
        switch (i){
            case 1: return Result.fail("库存不足");
            case 2: return Result.fail("不允许重复下单");
            case 3: return Result.fail("秒杀优惠券尚未开始");
            case 4: return Result.fail("秒杀优惠券已结束");
            case 5: return Result.fail("无此秒杀优惠券活动");
        }

        // 获取代理对象(事务)
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 保存队列
        // i == 0 返回订单id
        return Result.ok(orderId);
    }

//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
//
//    // 内部类: 线程任务
//    private class VoucherOrderHandler implements Runnable{
//
//        @Override
//        public void run() {
//            while (true){
//                try {
//                    // 获取队列中订单信息
//                    VoucherOrder voucherOrder = orderTasks.take(); // take 获取队列中的头部
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常" ,e);
//                }
//            }
//        }
//    }

    @Override
    public Result seckillVoucher_Lua(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        Long execute = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString());
//        int i = Objects.requireNonNull(execute).intValue();
//        switch (i){
//            case 1: return Result.fail("库存不足");
//            case 2: return Result.fail("不允许重复下单");
//            case 3: return Result.fail("秒杀优惠券尚未开始");
//            case 4: return Result.fail("秒杀优惠券已结束");
//            case 5: return Result.fail("无此秒杀优惠券活动");
//        }
//
//        // i == 0 返回订单id
//        long orderId = redisIdWorker.nextId("order");
//
//        // 有购买资格,把下单信息保存到阻塞队列 (创建订单)
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // 订单id
//        voucherOrder.setId(orderId);
//        // 用户id
//        voucherOrder.setUserId(userId);
//        // 代金券id
//        voucherOrder.setVoucherId(voucherId);
//
//        // 保存到阻塞队列
//        orderTasks.add(voucherOrder);
//
//        // 获取代理对象(事务)
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        // 保存队列
//        return Result.ok(orderId);
        return null;
    }

    @Override
    @Transactional
    public void createVoucherOrder_Lua(VoucherOrder voucherOrder) {
        // 获取用户
//        Long userId = UserHolder.getUser().getId(); // 因为现在是子线程 是拿不到ThreadLocal
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // 查询订单
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.error("用户已经购买一次");
            return;
        }
// 5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock -1
                .eq("voucher_id", voucherId)
//                .eq("stock" ,seckillVoucher.getStock()) // where id = ? and stock = ?
                .gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }
// 6.创建订单
        save(voucherOrder);
        // TODO: 因为是异步，所以不用返回信息给用户了
        // 7.返回订单id
//        return Result.ok(orderId);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
// 2.判断秒杀是否开始
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            // 秒杀是否开始
            return Result.fail("秒杀优惠券尚未开始");
        }
// 3.判断秒杀是否已经结束
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀优惠券已结束");
        }
// 4.判断库存是否充足
        if(seckillVoucher.getStock() < 1){
            return Result.fail("库存不足");
        }

// 7.返回订单id
        // 一人只能下一单 where user_id = ? and voucher_id = ?
        Long userId = UserHolder.getUser().getId();

//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        // waitTime 默认-1 不重试 ,leaseTime 默认30 超过30秒就自动释放 , timeUnit  时间单位
        boolean isLock = lock.tryLock();
        if(!isLock){ // 避免嵌套
            return Result.fail("不允许重复下单");
        }

        try {
            // 获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放
            lock.unlock();
        }
        /**
         synchronized (userId.toString().intern()) {
         // 获取代理对象(事务)
         IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
         //            return this.createVoucherOrder(voucherId);
         return proxy.createVoucherOrder(voucherId);
         }// 释放锁
         */
    }


    @Transactional
    public Result createVoucherOrder(Long voucherId){
        Long userId = UserHolder.getUser().getId();

        // 查询订单
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买一次");
        }
// 5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock -1
                .eq("voucher_id", voucherId)
//                .eq("stock" ,seckillVoucher.getStock()) // where id = ? and stock = ?
                .gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }
// 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 用户id
        voucherOrder.setUserId(userId);
        // 代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 7.返回订单id
        return Result.ok(orderId);
    }

}
