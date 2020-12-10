package com.github.hazork.mysouls.utils.db;

import java.time.LocalTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class CacheMap<K, V> extends ConcurrentHashMap<K, V> implements Runnable {

    private static final long serialVersionUID = -8795424293264578508L;

    private final long seconds;
    private final long delay;
    private final BiConsumer<K, V> callback;

    private ConcurrentHashMap<K, LocalTime> time_map = new ConcurrentHashMap<>();
    private Thread worker = new Thread(this, this.getClass().getSimpleName());

    public CacheMap(long delay, long seconds) {
	this(delay, seconds, null);
    }

    public CacheMap(long delay, long seconds, BiConsumer<K, V> callback) {
	this.delay = delay;
	this.seconds = seconds;
	this.callback = (callback == null) ? ((K, V) -> {}) : callback;
	tryRun();
    }

    @Override
    public V put(K key, V value) {
	time_map.put(key, LocalTime.now());
	V obj = super.put(key, value);
	tryRun();
	return obj;
    }

    @Override
    public V remove(Object key) {
	time_map.remove(key);
	return super.remove(key);
    }

    @Override
    public void run() {
	while (!isEmpty()) {
	    for (Entry<K, LocalTime> entry : time_map.entrySet()) {
		if (LocalTime.now().minusSeconds(seconds).isAfter(entry.getValue())) {
		    removeSafety(entry.getKey());
		}
		try {
		    Thread.sleep(delay);
		} catch (InterruptedException e) {
		    e.printStackTrace();
		    close();
		}
	    }
	}
    }

    public void removeSafety(K key) {
	if (callback != null) {
	    callback.accept(key, get(key));
	}
	this.remove(key);
    }

    public void close() {
	keySet().stream().forEach(this::removeSafety);
    }

    @Override
    public void clear() {
	super.clear();
	time_map.clear();
    }

    private void tryRun() {
	if (!worker.isAlive()) {
	    worker = new Thread(this);
	    worker.start();
	}
    }
}
