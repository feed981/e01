package com.feed01.utils;

public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 超时释放
     * @return true: 成功 ,false: 失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
