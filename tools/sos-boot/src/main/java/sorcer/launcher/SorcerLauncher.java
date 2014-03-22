/*
 * Copyright 2014 Sorcersoft.com S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sorcer.launcher;

import com.sun.jini.start.LifeCycle;
import org.rioproject.logging.ServiceLogEventHandlerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import sorcer.boot.ServiceStarter;
import sorcer.installer.Installer;
import sorcer.resolver.Resolver;
import sorcer.util.ConfigurableThreadFactory;
import sorcer.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ThreadFactory;

import static sorcer.core.SorcerConstants.P_MONITOR_INITIAL_OPSTRINGS;
import static sorcer.util.Collections.i;
import static sorcer.util.Collections.toProperties;

/**
 * @author Rafał Krupiński
 */
public class SorcerLauncher extends Launcher {
    final private static Logger log = LoggerFactory.getLogger(SorcerLauncher.class);

    private ThreadFactory threadFactory;
    private ServiceStarter serviceStarter;
    protected Profile profile;

    @Override
    public void preConfigure() {
        if (serviceStarter != null)
            throw new IllegalStateException("This instance has already created an object");

        super.preConfigure();
        updateMonitorConfig();
        configure();
        configureThreadFactory();
        postInstall();
    }

    private void configureThreadFactory() {
        if (threadFactory == null)
            threadFactory = getDefaultThreadFactory();
    }

    public static ThreadFactory getDefaultThreadFactory() {
        ConfigurableThreadFactory tf = new ConfigurableThreadFactory();
        tf.setNameFormat("SORCER boot");
        tf.setThreadGroup(new ThreadGroup("SORCER group"));
        return tf;
    }

    private void postInstall() {
        try {
            Installer installer = new Installer();
            if (installer.isInstallRequired(logDir))
                installer.install();
        } catch (RuntimeException x) {
            throw x;
        } catch (Exception x) {
            throw new RuntimeException("Error while installing SORCER", x);
        }
    }

    public void start() {
        SorcerShutdownHook.instance.add(this);

        Thread thread = threadFactory.newThread(new SorcerRunnable(getConfigs()));
        thread.start();
    }

    protected void configure() {
        //TODO RKR check grant
        Properties defaults = new Properties();
        defaults.putAll(properties);

        Properties overrides = new Properties(defaults);
        overrides.putAll(System.getProperties());

        if (log.isDebugEnabled())
            for (Object key : i(overrides.propertyNames()))
                log.debug("{} = {}", key, overrides.getProperty((String) key));

        System.setProperties(overrides);
    }

    private void updateMonitorConfig() {
        if (profile != null && profile.getMonitorConfigPaths() != null)
            if (rioConfigs == null)
                rioConfigs = Arrays.asList(profile.getMonitorConfigPaths());
            else
                Collections.addAll(rioConfigs, profile.getMonitorConfigPaths());

        if (rioConfigs == null || rioConfigs.isEmpty())
            return;

        List<String> paths = new ArrayList<String>(rioConfigs.size());
        for (String path : rioConfigs) {
            path = evaluator.eval(path);
            if (new File(path).exists())
                paths.add(path);
        }
        properties.put(P_MONITOR_INITIAL_OPSTRINGS, StringUtils.join(paths, File.pathSeparator));
    }

    public static boolean checkEnvironment() throws MalformedURLException {
        //we can't simply create another AppClassLoader,
        //because rio CommonClassLoader enforces SystemClassLoader as parent,
        //so all services started with rio would have parallel CL hierarchy

        List<URL> requiredClassPath = new LinkedList<URL>();
        for (String file : CLASS_PATH) {
            requiredClassPath.add(new File(Resolver.resolveAbsolute(file)).toURI().toURL());
        }
        List<URL> actualClassPath = getClassPath(SorcerLauncher.class.getClassLoader());
        List<URL> missingClassPath = new LinkedList<URL>(requiredClassPath);
        missingClassPath.removeAll(actualClassPath);

        // use logger, we won't be able to start in direct mode anyway
        for (URL entry : missingClassPath)
            log.warn("Missing required ClassPath element {}", entry);

        return missingClassPath.isEmpty();
    }

    static List<URL> getClassPath(ClassLoader classLoader) {
        List<URL> result = new ArrayList<URL>();
        ClassLoader cl = classLoader;
        do {
            if (cl instanceof URLClassLoader) {
                URL[] urls = ((URLClassLoader) cl).getURLs();
                Collections.addAll(result, urls);
            }
        } while ((cl = cl.getParent()) != null);
        return result;
    }

    protected List<String> getConfigs() {
        List<String> result = new ArrayList<String>(configs);
        if (profile != null) {
            String[] sorcerConfigPaths = profile.getSorcerConfigPaths();

            for (String cfg : sorcerConfigPaths) {
                String path = evaluator.eval(cfg);
                if (new File(path).exists())
                    result.add(path);
            }
        }
        return result;
    }

    @Override
    protected Properties getEnvironment() {
        return toProperties(System.getenv());
    }

    @Override
    public void setProfile(String profileName) {
        try {
            if (profileName.endsWith(".xml"))
                profile = Profile.load(new File(profileName).toURI().toURL());
            else
                profile = Profile.loadBuiltin(profileName);
        } catch (IOException x) {
            throw new IllegalArgumentException("Could not load profile " + profileName, x);
        }
    }

    public static void installSecurityManager() {
        if (System.getSecurityManager() == null)
            System.setSecurityManager(new SecurityManager());
    }

    public static void installLogging() {
        //redirect java.util.logging to slf4j/logback
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        ServiceLogEventHandlerHelper.addServiceLogEventHandler();
    }

    private class SorcerRunnable implements Runnable {
        private List<String> configs;

        public SorcerRunnable(List<String> configs) {
            this.configs = configs;
        }

        @Override
        public void run() {
            try {
                serviceStarter = new ServiceStarter(new LifeCycle() {
                    private boolean closing;

                    @Override
                    synchronized public boolean unregister(Object o) {
                        if (closing)
                            return false;
                        sorcerListener.sorcerEnded();
                        closing = true;
                        return true;
                    }
                });
                serviceStarter.start(configs);
                sorcerListener.sorcerStarted();
            } catch (Exception e) {
                log.error("Error while starting SORCER", e);
                sorcerListener.sorcerEnded();
            }
        }
    }

    @Override
    public void stop() {
        if (serviceStarter == null)
            return;
        serviceStarter.stop();
        serviceStarter = null;
    }

    public void setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
    }

}
