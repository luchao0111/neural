package org.micro.neural.limiter.core;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import lombok.extern.slf4j.Slf4j;
import org.micro.neural.config.store.IStore;
import org.micro.neural.config.store.RedisStore;
import org.micro.neural.config.store.StorePool;
import org.micro.neural.extension.Extension;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * The Limiter pf Redis.
 * <p>
 * 1.Limit instantaneous concurrent
 * 2.Limit the maximum number of requests for a time window
 * 3.Token Bucket
 *
 * @author lry
 **/
@Slf4j
@Extension("redis")
public class RedisLimiter extends AbstractCallLimiter {

    private StorePool storePool = StorePool.getInstance();
    private static String CONCURRENT_SCRIPT = getScript("/limiter_concurrent.lua");
    private static String RATE_SCRIPT = getScript("/limiter_rate.lua");
    private static String REQUEST_SCRIPT = getScript("/limiter_request.lua");

    @Override
    protected Acquire tryAcquireConcurrent() {
        IStore store = storePool.getStore();
        List<String> keys = new ArrayList<>();
        keys.add(limiterConfig.identity());
        List<Object> values = new ArrayList<>();
        values.add(limiterConfig.getMaxConcurrent());
        values.add(0);

        try {
            Integer result = store.eval(Integer.class, CONCURRENT_SCRIPT, limiterConfig.getConcurrentTimeout(), keys, values);
            return result == null ? Acquire.EXCEPTION : (result == 0 ? Acquire.FAILURE : Acquire.SUCCESS);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Acquire.EXCEPTION;
        }
    }

    @Override
    protected void releaseAcquireConcurrent() {
        IStore store = storePool.getStore();
        List<String> keys = new ArrayList<>();
        keys.add(limiterConfig.identity());
        List<Object> values = new ArrayList<>();
        values.add(limiterConfig.getMaxConcurrent());
        values.add(1);

        try {
            store.eval(Integer.class, CONCURRENT_SCRIPT, limiterConfig.getConcurrentTimeout(), keys, values);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    protected Acquire tryAcquireRate() {
        IStore store = storePool.getStore();
        List<String> keys = new ArrayList<>();
        keys.add(limiterConfig.identity());
        List<Object> values = new ArrayList<>();
        // permits_s 请求令牌数量
        values.add(limiterConfig.getMaxPermitRequest());
        // curr_mill_second_s 当前毫秒数
        values.add(limiterConfig.getMaxPermitRequest());
        // reserved_percent_s 桶中预留的令牌百分比，整数
        values.add(limiterConfig.getMaxPermitRequest());
        // max_wait_mill_second_s 最长等待多久。负值意味着给其它请求保留部分token
        values.add(limiterConfig.getRequestInterval());

        try {
            Integer result = store.eval(Integer.class, RATE_SCRIPT, limiterConfig.getRequestTimeout(), keys, values);
            return result == null ? Acquire.EXCEPTION : (result == 0 ? Acquire.FAILURE : Acquire.SUCCESS);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Acquire.EXCEPTION;
        }
    }

    @Override
    protected Acquire tryAcquireRequest() {
        IStore store = storePool.getStore();
        List<String> keys = new ArrayList<>();
        keys.add(limiterConfig.identity());
        List<Object> values = new ArrayList<>();
        values.add(limiterConfig.getMaxPermitRequest());
        values.add(limiterConfig.getRequestInterval());

        try {
            Integer result = store.eval(Integer.class, REQUEST_SCRIPT, limiterConfig.getRequestTimeout(), keys, values);
            return result == null ? Acquire.EXCEPTION : (result == 0 ? Acquire.FAILURE : Acquire.SUCCESS);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Acquire.EXCEPTION;
        }
    }

    private static String getScript(String name) {
        try {
            return CharStreams.toString(new InputStreamReader(
                    RedisStore.class.getResourceAsStream(name), Charsets.UTF_8));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

}
