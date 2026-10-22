package com.example.util;

import org.hibernate.Session;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManager;

/** Rapport synthetique base sur les statistiques Hibernate. */
public class PerformanceReport {

    private final EntityManager em;

    public PerformanceReport(EntityManager em) {
        this.em = em;
    }

    /**
     * Instantane des compteurs du cache L2 (hits, misses, puts).
     * On compare deux instantanes au lieu d'appeler clear() : le cumul du rapport final reste exact.
     */
    public static long[] compteurs(Statistics stats) {
        return new long[]{
                stats.getSecondLevelCacheHitCount(),
                stats.getSecondLevelCacheMissCount(),
                stats.getSecondLevelCachePutCount()
        };
    }

    public void printReport() {
        Statistics stats = em.unwrap(Session.class).getSessionFactory().getStatistics();

        System.out.println("=== RAPPORT DE PERFORMANCE ===");
        System.out.println("Sessions ouvertes             : " + stats.getSessionOpenCount());
        System.out.println("Transactions                  : " + stats.getTransactionCount());
        System.out.println("Entites chargees              : " + stats.getEntityLoadCount());
        System.out.println("Entites inserees              : " + stats.getEntityInsertCount());
        System.out.println("Entites mises a jour          : " + stats.getEntityUpdateCount());
        System.out.println("Entites supprimees            : " + stats.getEntityDeleteCount());
        System.out.println("Requetes HQL executees        : " + stats.getQueryExecutionCount());
        System.out.println("Requetes SQL preparees        : " + stats.getPrepareStatementCount());
        System.out.println("Cache L2 - hits               : " + stats.getSecondLevelCacheHitCount());
        System.out.println("Cache L2 - misses             : " + stats.getSecondLevelCacheMissCount());
        System.out.println("Cache L2 - puts               : " + stats.getSecondLevelCachePutCount());
        System.out.println("Duree max d'une requete       : " + stats.getQueryExecutionMaxTime() + " ms");
    }

    public void afficherRegions() {
        Statistics stats = em.unwrap(Session.class).getSessionFactory().getStatistics();
        System.out.println("--- Regions du cache de second niveau ---");
        String[] regions = stats.getSecondLevelCacheRegionNames();
        java.util.Arrays.sort(regions);
        for (String region : regions) {
            if (region.startsWith("default-")) {
                continue;
            }
            SecondLevelCacheStatistics regionStats = stats.getSecondLevelCacheStatistics(region);
            long hits = regionStats == null ? 0 : regionStats.getHitCount();
            long puts = regionStats == null ? 0 : regionStats.getPutCount();
            long misses = regionStats == null ? 0 : regionStats.getMissCount();
            System.out.println(region + " | hits = " + hits + " | misses = " + misses + " | puts = " + puts);
        }
    }
}
