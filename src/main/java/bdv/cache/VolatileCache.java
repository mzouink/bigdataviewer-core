package bdv.cache;

public interface VolatileCache
{
	public < K, V extends VolatileCacheValue >
		VolatileCacheEntry< K, V > put( final K key, final V value, final VolatileCacheValueLoader< K, V > loader );

	public < K, V extends VolatileCacheValue >
		VolatileCacheEntry< K, V > get( final K key );

	public void finalizeRemovedCacheEntries(); // TODO: rename to cleanUp() ?

	public void clearCache();
}
