/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.dependencytrack.upgrade;

import alpine.Config;
import alpine.logging.Logger;
import alpine.model.InstalledUpgrades;
import alpine.model.SchemaVersion;
import alpine.persistence.JdoProperties;
import alpine.upgrade.UpgradeException;
import alpine.upgrade.UpgradeExecutor;
import org.datanucleus.PersistenceNucleusContext;
import org.datanucleus.api.jdo.JDOPersistenceManagerFactory;
import org.datanucleus.store.schema.SchemaAwareStoreManager;
import org.dependencytrack.RequirementsVerifier;
import org.dependencytrack.persistence.QueryManager;
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class UpgradeInitializer implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(UpgradeInitializer.class);

    /**
     * {@inheritDoc}
     */
    public void contextInitialized(ServletContextEvent event) {
        LOGGER.info("Initializing upgrade framework");

        final String driverPath = Config.getInstance().getProperty(Config.AlpineKey.DATABASE_DRIVER_PATH);
        if (driverPath != null) {
            Config.getInstance().expandClasspath(driverPath);
        }
        final JDOPersistenceManagerFactory pmf  = (JDOPersistenceManagerFactory) JDOHelper.getPersistenceManagerFactory(JdoProperties.get(), "Alpine");

        // Ensure that the UpgradeMetaProcessor and SchemaVersion tables are created NOW, not dynamically at runtime.
        final PersistenceNucleusContext ctx = pmf.getNucleusContext();
        final Set<String> classNames = new HashSet<>();
        classNames.add(InstalledUpgrades.class.getCanonicalName());
        classNames.add(SchemaVersion.class.getCanonicalName());
        ((SchemaAwareStoreManager)ctx.getStoreManager()).createSchemaForClasses(classNames, new Properties());

        if (RequirementsVerifier.failedValidation()) {
            return;
        }
        final PersistenceManager pm = pmf.getPersistenceManager();
        final QueryManager qm = new QueryManager(pm);
        final UpgradeExecutor executor = new UpgradeExecutor(qm);

        try {
            executor.executeUpgrades(UpgradeItems.getUpgradeItems());
        }
        catch (UpgradeException e) {
            LOGGER.error("An error occurred performing upgrade processing. " + e.getMessage());
        }
        pm.close();
        pmf.close();
    }

    /**
     * {@inheritDoc}
     */
    public void contextDestroyed(ServletContextEvent event) {
        /* Intentionally blank to satisfy interface */
    }

}