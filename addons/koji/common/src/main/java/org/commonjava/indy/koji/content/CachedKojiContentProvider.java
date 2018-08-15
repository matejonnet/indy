package org.commonjava.indy.koji.content;

import com.redhat.red.build.koji.KojiClient;
import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveQuery;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiSessionInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;
import org.commonjava.indy.koji.conf.IndyKojiConfig;
import org.commonjava.indy.subsys.infinispan.CacheHandle;
import org.commonjava.indy.subsys.infinispan.CacheProducer;
import org.commonjava.maven.atlas.ident.ref.ArtifactRef;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.redhat.red.build.koji.model.xmlrpc.messages.Constants.GET_BUILD;

@ApplicationScoped
public class CachedKojiContentProvider
{
    private Logger logger = LoggerFactory.getLogger( getClass() );


    private final String KOJI_TAGS = "koji-tags"; // 99% immutable

    private final String KOJI_BUILD_INFO = "koji-buildInfoByIdOrNvr"; // immutable

    private final String KOJI_BUILD_INFO_CONTAINING_ARTIFACT = "koji_buildInfoContainingArtifact"; // volatile

    private final String KOJI_ARCHIVES_FOR_BUILD = "koji-ArchivesForBuild"; // immutable

    private final String KOJI_ARCHIVES_MATCHING_GA = "koji-ArchivesMatchingGA"; // volatile


    @Inject
    private CacheProducer cacheProducer;

    @Inject
    private KojiClient kojiClient;

    @Inject
    private IndyKojiConfig kojiConfig;

    public CachedKojiContentProvider()
    {
    }

    public CachedKojiContentProvider( KojiClient kojiClient, CacheProducer cacheProducer )
    {
        this.kojiClient = kojiClient;
        this.cacheProducer = cacheProducer;
    }

    /**
     * The caller actually passes SimpleArtifactRef which has right hashcode/equals so we can use it as cache key.
     */
    public List<KojiBuildInfo> listBuildsContaining( ArtifactRef artifactRef, KojiSessionInfo session )
                    throws KojiClientException
    {
        return computeIfAbsent( KOJI_BUILD_INFO_CONTAINING_ARTIFACT, ArtifactRef.class, List.class, artifactRef,
                                () -> kojiClient.listBuildsContaining( artifactRef, session ),
                                kojiConfig.getQueryCacheTimeoutHours() );
    }


    public List<KojiTagInfo> listTags( Integer buildId, KojiSessionInfo session ) throws KojiClientException
    {
        return computeIfAbsent( KOJI_TAGS, Integer.class, List.class, buildId,
                                () -> kojiClient.listTags( buildId, session ) );
    }

    public Map<Integer, List<KojiTagInfo>> listTags( List<Integer> buildIds, KojiSessionInfo session )
                    throws KojiClientException
    {
        logger.debug( "listTags, buildIds: {}", buildIds );

        if ( !kojiConfig.isQueryCacheEnabled() )
        {
            logger.trace( "Cache not enabled, run direct kojiClient.listTags" );
            return kojiClient.listTags( buildIds, session );
        }

        CacheHandle<Integer, List> cache = cacheProducer.getCache( KOJI_TAGS, Integer.class, List.class );

        Map<Integer, List<KojiTagInfo>> map = new HashMap<>();
        List<Integer> missed = new ArrayList<>();
        for ( Integer buildId : buildIds )
        {
            List ret = cache.get( buildId );
            if ( ret != null )
            {
                map.put( buildId, ret );
            }
            else
            {
                missed.add( buildId );
            }
        }
        if ( !missed.isEmpty() )
        {
            Map<Integer, List<KojiTagInfo>> retrieved = kojiClient.listTags( missed, session );
            map.putAll( retrieved );
            retrieved.forEach( ( k, v ) -> cache.put( k, v ) );
        }
        return map;
    }


    public KojiBuildInfo getBuildInfo( Integer buildId, KojiSessionInfo session ) throws KojiClientException
    {
        return computeIfAbsent( KOJI_BUILD_INFO, Object.class, KojiBuildInfo.class, buildId,
                                () -> kojiClient.getBuildInfo( buildId, session ) );
    }

    public KojiBuildInfo getBuildInfo( String nvr, KojiSessionInfo session ) throws KojiClientException
    {
        return computeIfAbsent( KOJI_BUILD_INFO, Object.class, KojiBuildInfo.class, nvr,
                                () -> kojiClient.getBuildInfo( nvr, session ) );
    }

    public List<KojiBuildInfo> getBuildInfo( List<Object> args, KojiSessionInfo session )
    {
        logger.debug( "getBuildInfo, args: {}", args );

        if ( !kojiConfig.isQueryCacheEnabled() )
        {
            logger.trace( "Cache not enabled, run direct kojiClient.multiCall" );
            return kojiClient.multiCall( GET_BUILD, args, KojiBuildInfo.class, session );
        }

        CacheHandle<Object, KojiBuildInfo> cache = cacheProducer.getCache( KOJI_BUILD_INFO, Object.class, KojiBuildInfo.class );

        Map<Object, KojiBuildInfo> m = new HashMap<>();
        List<Object> missed = new ArrayList<>();
        for ( Object obj : args )
        {
            KojiBuildInfo kojiBuildInfo = cache.get( obj );
            if ( kojiBuildInfo != null )
            {
                m.put( obj, kojiBuildInfo );
            }
            else
            {
                missed.add( obj );
            }
        }
        if ( !missed.isEmpty() )
        {
            List<KojiBuildInfo> retrieved = kojiClient.multiCall( GET_BUILD, missed, KojiBuildInfo.class, session );
            for ( int i = 0; i < missed.size(); i++ )
            {
                Object obj = missed.get( i );
                KojiBuildInfo kojiBuildInfo = retrieved.get( i );
                m.put( obj, kojiBuildInfo );
                cache.put( obj, kojiBuildInfo );
            }
        }

        List<KojiBuildInfo> ret = new ArrayList<>();
        args.forEach( (a) -> ret.add( m.get( a ) ) ); // keep the order of results equal to args
        return ret;
    }


    public List<KojiArchiveInfo> listArchivesMatching( ProjectRef ga, KojiSessionInfo session )
                    throws KojiClientException
    {
        KojiArchiveQuery query = new KojiArchiveQuery( ga );
        return computeIfAbsent( KOJI_ARCHIVES_MATCHING_GA, ProjectRef.class, List.class, ga,
                                () -> kojiClient.listArchives( query, session ),
                                kojiConfig.getQueryCacheTimeoutHours() );
    }

    public List<KojiArchiveInfo> listArchivesForBuild( Integer buildId, KojiSessionInfo session )
                    throws KojiClientException
    {
        KojiArchiveQuery query = new KojiArchiveQuery().withBuildId( buildId );
        return computeIfAbsent( KOJI_ARCHIVES_FOR_BUILD, Integer.class, List.class, buildId,
                                () -> kojiClient.listArchives( query, session ) );
    }


    @FunctionalInterface
    private interface KojiContentSupplier<T>
    {
        T getKojiContent() throws KojiClientException;
    }

    private <K, V> V computeIfAbsent( String name, Class<K> kType, Class<V> vType, K key,
                                      KojiContentSupplier<V> supplier ) throws KojiClientException
    {
        return computeIfAbsent( name, kType, vType, key, supplier, 0 );
    }

    private <K, V> V computeIfAbsent( String name, Class<K> kType, Class<V> vType, K key,
                                      KojiContentSupplier<V> supplier, int expirationHours )
                    throws KojiClientException
    {
        logger.debug( "computeIfAbsent, cache: {}, key: {}", name, key );

        if ( !kojiConfig.isQueryCacheEnabled() )
        {
            logger.trace( "Cache not enabled, run direct supplier.getKojiContent" );
            return supplier.getKojiContent();
        }

        CacheHandle<K, V> cache = cacheProducer.getCache( name, kType, vType );
        V ret = cache.get( key );
        if ( ret == null )
        {
            logger.trace( "Entry not found, run supplier and put, expirationHours: {}", expirationHours );
            ret = supplier.getKojiContent();
            if ( expirationHours > 0 )
            {
                cache.put( key, ret, expirationHours, TimeUnit.HOURS );
            }
            else
            {
                cache.put( key, ret );
            }
        }

        if ( ret instanceof List )
        {
            ret = (V) new ArrayList( (List) ret ); // protective copy
        }
        logger.trace( "Return value, cache: {}, key: {}, ret: {}", name, key, ret );
        return ret;
    }

}
