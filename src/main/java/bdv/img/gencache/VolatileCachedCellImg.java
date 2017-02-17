package bdv.img.gencache;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;

import bdv.cache.CacheControl;
import bdv.img.gencache.VolatileCachedCellImg.VolatileCachedCells;
import net.imglib2.cache.iotiming.IoTimeBudget;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.NativeImg;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.list.AbstractLongListImg;
import net.imglib2.type.NativeType;

/**
 *
 * @param <T>
 * @param <A>
 *
 * @author Tobias Pietzsch
 * @author Stephan Saalfeld
 */
public class VolatileCachedCellImg< T extends NativeType< T >, A >
		extends AbstractCellImg< T, A, Cell< A >, VolatileCachedCells< Cell< A > > >
{
	@FunctionalInterface
	public interface Get< T >
	{
		T get( long index, CacheHints cacheHints );

	}

	@FunctionalInterface
	public interface CheckedGet< T >
	{
		T get( long index, CacheHints cacheHints ) throws ExecutionException;
	}

	public static < T > Get< T > unchecked( final CheckedGet< T > checked )
	{
		return ( index, hints ) -> {
			try
			{
				return checked.get( index, hints );
			}
			catch ( final ExecutionException e )
			{
				throw new RuntimeException( e );
			}
		};
	}

	public VolatileCachedCellImg( final CellGrid grid, final T type, final CacheHints cacheHints, final CheckedGet< Cell< A > > get )
	{
		this( grid, type, cacheHints, unchecked( get ) );
	}

	public VolatileCachedCellImg( final CellGrid grid, final T type, final CacheHints cacheHints, final Get< Cell< A > > get )
	{
		super( grid, new VolatileCachedCells<>( grid.getGridDimensions(), get, null ), type.getEntitiesPerPixel() );
		cells.cacheHints = cacheHints;
		try
		{
			linkType( type, this );
		}
		catch ( NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e )
		{
			throw new RuntimeException( e );
		}
	}

	/**
	 * Set {@link CacheHints hints} on how to handle cell requests for this
	 * cache. The hints comprise {@link LoadingStrategy}, queue priority, and
	 * queue order.
	 * <p>
	 * Whenever a cell is accessed its data may be invalid, meaning that the
	 * cell data has not been loaded yet. In this case, the
	 * {@link LoadingStrategy} determines when the data should be loaded:
	 * <ul>
	 * <li>{@link LoadingStrategy#VOLATILE}: Enqueue the cell for asynchronous
	 * loading by a fetcher thread, if it has not been enqueued in the current
	 * frame already.
	 * <li>{@link LoadingStrategy#BLOCKING}: Load the cell data immediately.
	 * <li>{@link LoadingStrategy#BUDGETED}: Load the cell data immediately if
	 * there is enough {@link IoTimeBudget} left for the current thread group.
	 * Otherwise enqueue for asynchronous loading, if it has not been enqueued
	 * in the current frame already.
	 * <li>{@link LoadingStrategy#DONTLOAD}: Do nothing.
	 * </ul>
	 * <p>
	 * If a cell is enqueued, it is enqueued in the queue with the specified
	 * {@link CacheHints#getQueuePriority() queue priority}. Priorities are
	 * consecutive integers <em>0 ... n-1</em>, where 0 is the highest priority.
	 * Requests with priority <em>i &lt; j</em> will be handled before requests
	 * with priority <em>j</em>.
	 * <p>
	 * Finally, the {@link CacheHints#isEnqueuToFront() queue order} determines
	 * whether the cell is enqueued to the front or to the back of the queue
	 * with the specified priority.
	 * <p>
	 * Note, that the queues are {@link BlockingFetchQueues#clearToPrefetch()
	 * cleared} whenever a {@link CacheControl#prepareNextFrame() new frame} is
	 * rendered.
	 *
	 * @param cacheHints
	 *            describe handling of cell requests for this cache.
	 */
	public void setCacheHints( final CacheHints cacheHints )
	{
		cells.cacheHints = cacheHints;
	}

	@Override
	public ImgFactory< T > factory()
	{
		throw new UnsupportedOperationException( "not implemented yet" );
	}

	@Override
	public Img< T > copy()
	{
		throw new UnsupportedOperationException( "not implemented yet" );
	}

	public static final class VolatileCachedCells< T > extends AbstractLongListImg< T >
	{
		private final Get< T > get;

		CacheHints cacheHints;

		protected VolatileCachedCells( final long[] dimensions, final Get< T > get, final CacheHints cacheHints )
		{
			super( dimensions );
			this.get = get;
			this.cacheHints = cacheHints;
		}

		@Override
		protected T get( final long index )
		{
			return get.get( index, cacheHints );
		}

		@Override
		protected void set( final long index, final T value )
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public ImgFactory< T > factory()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public Img< T > copy()
		{
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * Reflection hack because there is no T NativeType<T>.create(NativeImg<?, A>) method in ImgLib2
	 * Note that for this method to be introduced, NativeType would need an additional generic parameter A
	 * that specifies the accepted family of access objects that can be used in the NativeImg... big change
	 *
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	static void linkType( final NativeType t, final NativeImg img ) throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException
	{
		final Constructor constructor = t.getClass().getDeclaredConstructor( NativeImg.class );
		if ( constructor != null )
		{
			final NativeType linkedType = ( NativeType )constructor.newInstance( img );
			img.setLinkedType( linkedType );
		}
	}
}
