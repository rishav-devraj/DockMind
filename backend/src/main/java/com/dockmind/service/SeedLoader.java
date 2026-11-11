package com.dockmind.service;

import com.dockmind.domain.PurchaseOrderRepository;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/** Runs during context start, before the web server opens, so no request can see empty tables. */
@Component
public class SeedLoader {
    private final PurchaseOrderRepository purchaseOrders;
    private final DataSource dataSource;

    public SeedLoader(PurchaseOrderRepository purchaseOrders, DataSource dataSource) {
        this.purchaseOrders = purchaseOrders;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void load() {
        if (purchaseOrders.count() == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("seed.sql")).execute(dataSource);
        }
    }
}
