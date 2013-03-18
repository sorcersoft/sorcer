/*
s * Copyright 2009 the original author or authors.
 * Copyright 2009 SorcerSoft.org.
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
package sorcer.core.provider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.Permission;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.transaction.Transaction;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.Uuid;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceIDListener;
import net.jini.lookup.entry.UIDescriptor;
import net.jini.lookup.ui.MainUI;
import net.jini.lookup.ui.factory.JFrameFactory;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.proxytrust.TrustEquivalence;
import sorcer.core.AccessDeniedException;
import sorcer.core.AdministratableProvider;
import sorcer.core.Provider;
import sorcer.core.SorcerConstants;
import sorcer.core.UEID;
import sorcer.core.UnknownExertionException;
import sorcer.core.context.ContextManagement;
import sorcer.core.context.ControlContext;
import sorcer.core.context.RemoteContextManagement;
import sorcer.core.context.ServiceContext;
import sorcer.core.dispatch.DispatcherException;
import sorcer.core.dispatch.MonitoredTaskDispatcher;
import sorcer.core.provider.proxy.Outer;
import sorcer.core.provider.proxy.Partner;
import sorcer.core.provider.proxy.Partnership;
import sorcer.core.provider.ui.ProviderUI;
import sorcer.falcon.base.Conditional;
import sorcer.service.Context;
import sorcer.service.ContextException;
import sorcer.service.ExecState;
import sorcer.service.Exertion;
import sorcer.service.ExertionException;
import sorcer.service.Job;
import sorcer.service.Jobber;
import sorcer.service.MonitorException;
import sorcer.service.ServiceExertion;
import sorcer.service.Signature;
import sorcer.service.SignatureException;
import sorcer.service.Spacer;
import sorcer.service.Task;
import sorcer.ui.exertlet.NetletEditor;
import sorcer.ui.exertlet.NetletUI;
import sorcer.ui.serviceui.UIComponentFactory;
import sorcer.ui.serviceui.UIDescriptorFactory;
import sorcer.ui.serviceui.UIFrameFactory;
import sorcer.util.ObjectLogger;
import sorcer.util.Sorcer;
import sorcer.util.SorcerUtil;
import sorcer.util.bdb.sdb.SdbURLStreamHandlerFactory;

import com.sun.jini.config.Config;
import com.sun.jini.start.LifeCycle;
import com.sun.jini.thread.TaskManager;

/**
 * The ServiceProvider class is a type of {@link ServiceExerter} with dependency
 * injection defined by a Jini 2 configuration, proxy management, and own
 * service discovery management for registering its proxies. In the simplest
 * case, the provider exports and registers its own (outer) proxy with the
 * primary method {@link Provider#service(Exertion)}. The functionality of an
 * outer proxy can be extended by its inner server functionality with its Remote
 * inner proxy. In this case, the outer proxies have to implement {@link Outer}
 * interface and each outer proxy is registered with the inner proxy allocated
 * with {@link Outer#setProxy} invoked on the outer proxy. Obviously an outer
 * proxy can implement own interfaces with the help of its embedded inner proxy
 * that in turn can consit of multiple own inner proxies as needed. This class
 * implements the {@link Outer} interface, so can extend its functionality by
 * inner proxying.
 * <p>
 * A smart proxy can be defined in the provider's Jini configuration by the
 * <code>smartProxy</code> entry. This proxy does not represent directly any
 * exported server, it is registered with lookup services as is. However, the
 * smart proxy (implementing {@link Outer} interface) can extend its
 * functionality by setting the providers outer proxy as its inner proxy. Thus,
 * smart proxies that implement{@link Outer}, called <i>semismart</i> contain
 * this provider's proxy as its inner proxy. Smart proxies that do not extend
 * its functionality via its inner proxy are called fat proxies. Fat proxies do
 * not make remote calls back to their providers and the providers just maintain
 * lookup service registrations.
 * <p>
 * An inner or outer proxy can be a surrogate of a service partner defined by
 * this provider using <code>server</code> configuration entry or two realted
 * entries: <code>serverType</code> and <code>serverName</code>. If the entry
 * <code>server</code> is defined and its exporter is defined by the entry
 * <code>serverExporter</code> then this provider will use the server's proxy as
 * the outer (primary proxy) and itself as the inner proxy. However if the
 * exporter is not defined then the provider's proxy is primary (outer) and the
 * server's proxy is the inner one.
 * <p>
 * On the other hand, if the <code>server</code> entry is not defined but at
 * least <code>serverType</code> is defined then the instance of server is
 * created and exported if the entry <code>serverExporter</code> is given. The
 * exported server proxy becomes the primary provider's proxy. However, if no
 * exporter is defined then server proxy becomes the inner proxy of this
 * provider's proxy (outer proxy). Thus, exported servers use outer proxies
 * while not exported user inner proxies of this provider. In this context, a
 * smart proxy implementing {@link Outer} interface can get the outer proxy and
 * its inner proxy composed in either direction provider/server proxy
 * relationship.
 * <p>
 * A service method is a method returning {@link ServiceContext} and having a
 * single parameter as {@link ServiceContext}. Service beans are components that
 * implement interfaces in terms of service methods only. A list of service
 * beans can be specified in a provider's Jini configuration as the
 * <code>beans</code> entry. In this case a proxy implementing all interfaces
 * implemented by service beans are dynamically created and registered with
 * lookup services. Multiple SORECER servers can be deployed within a single
 * {@link ServiceProvider} as its own service beans.
 * 
 * @see Proivider
 * @see ServiceIDListener
 * @see ReferentUuuid
 * @see AdministratableProvider
 * @see ProxyAccessor
 * @see ServerProxyTrust
 * @see RemoteMethodControl 
 * @see LifeCycle
 * @see Partener
 * @see Partnership
 * @see RemoteContextManagement 
 * @see SorcerConstants
 */
public class ServiceProvider implements Provider, ServiceIDListener,
		ReferentUuid, AdministratableProvider, ProxyAccessor, ServerProxyTrust,
		RemoteMethodControl, LifeCycle, Partner, Partnership,
		RemoteContextManagement, SorcerConstants {
	// RemoteMethodControl is needed to enable Proxy Constraints

	static {
		//Handler.register();
		URL.setURLStreamHandlerFactory(new SdbURLStreamHandlerFactory());
	}
	
	public static final String COMPONENT = ServiceProvider.class.getName();

	/** Logger and configuration component name for service provider. */
	static final String PROVIDER = ServiceProvider.class.getName();

	/** Logger for logging information about this instance */
	protected static final Logger logger = Logger.getLogger(PROVIDER);

	protected ProviderDelegate delegate;

	static final String DEFAULT_PROVIDER_PROPERTY = "provider.properties";

	protected TaskManager threadManager;

	int loopCount = 0;

	/** The login context, for logging out */
	private LoginContext loginContext;

	/** The provider's JoinManager. */
	private JoinManager joinManager;

	private LookupDiscoveryManager ldmgr;

	// the number of shared providers
	protected static int tally = 0;

	/** Object to notify when this service is destroyed, or null. */
	private LifeCycle lifeCycle;

	// all providers in the same shared JVM
	private static List<ServiceProvider> providers = new ArrayList<ServiceProvider>();

	private ClassLoader serviceClassLoader;

	private String[] accessorGroups = DiscoveryGroupManagement.ALL_GROUPS;
	
	protected ServiceProvider() throws RemoteException {
		providers.add(this);
		delegate = new ProviderDelegate();
		delegate.provider = this;
	}

	/**
	 * Required constructor for Jini 2 NonActivatableServiceDescriptors
	 * 
	 * @param args
	 * @param lifeCycle
	 * @throws Exception
	 */
	public ServiceProvider(String[] args, LifeCycle lifeCycle) throws Exception {
		this();
		// count initialized shared providers
		tally = tally + 1;
		// load Sorcer environment properties via static initializer
		Sorcer.getProperties();
		serviceClassLoader = Thread.currentThread().getContextClassLoader();
		final Configuration config = ConfigurationProvider.getInstance(
		// args, delegate.getClass().getClassLoader());
				args, serviceClassLoader);
		delegate.setJiniConfig(config);
		// inspect class loader tree
		// com.sun.jini.start.ClassLoaderUtil.displayContextClassLoaderTree();
		// System.out.println("service provider class loader: " +
		// serviceClassLoader);
		String providerProperties = null;
		try {
			providerProperties = (String) Config.getNonNullEntry(config,
					COMPONENT, "properties", String.class, "");
		} catch (ConfigurationException e) {
			// e.printStackTrace();
			logger.throwing(ServiceProvider.class.getName(), "init", e);
		}
		// configure the provider's delegate
		delegate.getProviderConfig().init(true, providerProperties);
		delegate.configure(config);
		// decide if thread management is needed for ServiceExerter
		setupThreadManager();
		init(args, lifeCycle);
	}

	// Implement ServerProxyTrust
	/**
	 * @throws UnsupportedOperationException
	 *             if the server proxy does not implement both
	 *             {@link RemoteMethodControl}and {@linkTrustEquivalence}
	 */

	public Permission[] getGrants(Class<?> cl, Principal[] principals) {
		return null;
	}

	public void grant(Class<?> cl, Principal[] principals, Permission[] permissions) {
	}

	public boolean grantSupported() {
		return false;
	}

	public TrustVerifier getProxyVerifier() {
		return delegate.getProxyVerifier();
	}

	public MethodConstraints getConstraints() {
		return null;
	}

	public RemoteMethodControl setConstraints(MethodConstraints constraints) {
		return null;
	}

	/**
	 * Returns an object that implements whatever administration interfaces are
	 * appropriate for the particular service.
	 * 
	 * @return an object that implements whatever administration interfaces are
	 *         appropriate for the particular service.
	 */
	public Object getAdmin() {
		return delegate.getAdmin();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see sorcer.core.provider.OuterProxy#setAdmin(java.lang.Object)
	 */
	public void setAdmin(Object proxy) {
		delegate.setAdmin(proxy);
	}

	/**
	 * Get the current attribute sets for the service.
	 * 
	 * @return the current attribute sets for the service
	 */
	public Entry[] getLookupAttributes() {
		return joinManager.getAttributes();
	}

	/**
	 * Add attribute sets for the service. The resulting set will be used for
	 * all future joins. The attribute sets are also added to all
	 * currently-joined lookup services.
	 * 
	 * @param: attrSets the attribute sets to add
	 * @see net.jini.admin.JoinAdmin#addLookupAttributes(net.jini.core.entry.Entry[])
	 */
	public void addLookupAttributes(Entry[] attrSets) {
		joinManager.addAttributes(attrSets, true);
		logger.log(Level.CONFIG, "Added attributes");
	}

	/**
	 * Modify the current attribute sets, using the same semantics as
	 * ServiceRegistration.modifyAttributes. The resulting set will be used for
	 * all future joins. The same modifications are also made to all
	 * currently-joined lookup services.
	 * 
	 * @param attrSetTemplates
	 *            - the templates for matching attribute sets
	 * @param attrSets
	 *            the modifications to make to matching sets
	 * @exception RemoteException
	 * @see net.jini.admin.JoinAdmin#modifyLookupAttributes(net.jini.core.entry.Entry[],
	 *      net.jini.core.entry.Entry[])
	 */
	public void modifyLookupAttributes(Entry[] attrSetTemplates,
			Entry[] attrSets) {
		joinManager.modifyAttributes(attrSetTemplates, attrSets, true);
		logger.log(Level.CONFIG, "Modified attributes");
	}

	/**
	 * Get the list of groups to join. An empty array means the service joins no
	 * groups (as opposed to "all" groups).
	 * 
	 * @return an array of groups to join. An empty array means the service
	 *         joins no groups (as opposed to "all" groups).
	 * 
	 * @exception RemoteException
	 * @see net.jini.admin.JoinAdmin#getLookupGroups()
	 */
	public String[] getLookupGroups() {
		return ldmgr.getGroups();
	}

	/**
	 * Add new groups to the set to join. Lookup services in the new groups will
	 * be discovered and joined.
	 * 
	 * @param groups
	 *            groups to join
	 * @exception RemoteException
	 * @see net.jini.admin.JoinAdmin#addLookupGroups(java.lang.String[])
	 */
	public void addLookupGroups(String[] groups) {
		try {
			ldmgr.addGroups(groups);
		} catch (Exception e) {
			if (logger.isLoggable(Level.SEVERE)) {
				logger.log(Level.SEVERE, "Error while adding groups : {0}", e);
			}
		}
		if (logger.isLoggable(Level.CONFIG)) {
			logger.log(Level.CONFIG, "Added lookup groups: {0}",
					SorcerUtil.arrayToString(groups));
		}
	}

	/**
	 * Remove groups from the set to join. Leases are cancelled at lookup
	 * services that are not members of any of the remaining groups.
	 * 
	 * @param groups
	 *            groups to leave
	 * @exception RemoteException
	 * @see net.jini.admin.JoinAdmin#removeLookupGroups(java.lang.String[])
	 */
	public void removeLookupGroups(String[] groups) {
		try {
			ldmgr.removeGroups(groups);
		} catch (Exception e) {
			if (logger.isLoggable(Level.SEVERE)) {
				logger.log(Level.SEVERE, "Error while removing groups : {0}", e);
			}
		}

		if (logger.isLoggable(Level.CONFIG)) {
			logger.log(Level.CONFIG, "Removed lookup groups: {0}",
					SorcerUtil.arrayToString(groups));
		}
	}

	/**
	 * Replace the list of groups to join with a new list. Leases are cancelled
	 * at lookup services that are not members of any of the new groups. Lookup
	 * services in the new groups will be discovered and joined.
	 * 
	 * @param groups
	 *            groups to join
	 * @exception RemoteException
	 * @see net.jini.admin.JoinAdmin#setLookupGroups(java.lang.String[])
	 */
	public void setLookupGroups(String[] groups) {
		try {
			ldmgr.setGroups(groups);
		} catch (Exception e) {
			if (logger.isLoggable(Level.SEVERE)) {
				logger.log(Level.SEVERE, "Error while setting groups : {0}", e);
			}
		}

		if (logger.isLoggable(Level.CONFIG)) {
			logger.log(Level.CONFIG, "Set lookup groups: {0}",
					SorcerUtil.arrayToString(groups));
		}
	}

	/**
	 * Get the list of locators of specific lookup services to join.
	 * 
	 * @return the list of locators of specific lookup services to join
	 * @exception RemoteException
	 * @see net.jini.admin.JoinAdmin#getLookupLocators()
	 */
	public LookupLocator[] getLookupLocators() {
		return ldmgr.getLocators();
	}

	/**
	 * Add locators for specific new lookup services to join. The new lookup
	 * services will be discovered and joined.
	 * 
	 * @param locators
	 *            locators of specific lookup services to join
	 * @exception RemoteException
	 * @see net.jini.admin.JoinAdmin#addLookupLocators(net.jini.core.discovery.LookupLocator[])
	 */
	public void addLookupLocators(LookupLocator[] locators)
			throws RemoteException {
		// for (int i = locators.length; --i >= 0; ) {
		// locators[i] = (LookupLocator)
		// locatorPreparer.prepareProxy(locators[i]);
		// }
		ldmgr.addLocators(locators);
		if (logger.isLoggable(Level.CONFIG)) {
			logger.log(Level.CONFIG, "Added lookup locators: {0}",
					SorcerUtil.arrayToString(locators));
		}
	}

	/**
	 * Remove locators for specific lookup services from the set to join. Any
	 * leases held at the lookup services are cancelled.
	 * 
	 * @param locators
	 *            locators of specific lookup services to leave
	 * @exception RemoteException
	 * @see net.jini.admin.JoinAdmin#removeLookupLocators(net.jini.core.discovery.LookupLocator[])
	 */
	public void removeLookupLocators(LookupLocator[] locators)
			throws RemoteException {
		// for (int i = locators.length; --i >= 0; ) {
		// locators[i] = (LookupLocator) locatorPreparer.prepareProxy(
		// locators[i]);
		// }
		ldmgr.removeLocators(locators);
		if (logger.isLoggable(Level.CONFIG)) {
			logger.log(Level.CONFIG, "Removed lookup locators: {0}",
					SorcerUtil.arrayToString(locators));
		}
	}

	// Inherit java doc from super type
	public void setLookupLocators(LookupLocator[] locators)
			throws RemoteException {
		// for (int i = locators.length; --i >= 0; ) {
		// locators[i] = (LookupLocator)
		// locatorPreparer.prepareProxy(locators[i]);
		// }
		ldmgr.setLocators(locators);
		if (logger.isLoggable(Level.CONFIG)) {
			logger.log(Level.CONFIG, "Set lookup locators: {0}",
					SorcerUtil.arrayToString(locators));
		}
	}

	/**
	 * Method invoked by a server to inform the LifeCycle object that it can
	 * release any resources associated with the server.
	 * 
	 * @param impl
	 *            Object reference to the implementation object created by the
	 *            NonActivatableServiceDescriptor. This reference must be equal,
	 *            in the "==" sense, to the object created by the
	 *            NonActivatableServiceDescriptor.
	 * @return true if the invocation was successfully processed and false
	 *         otherwise.
	 */
	public boolean unregister(Object impl) {
		logger.log(Level.INFO, "Unregistering service");
		if (this == impl)
			try {
				this.destroy();
			} catch (RemoteException re) {
				re.printStackTrace();
			}
		return true;
	}

	/**
	 * This method spawns a separate thread to destroy this provider after 2
	 * sec, should make a reasonable attempt to let this remote call return
	 * successfully.
	 */
	private class DestroyThread implements Runnable {
		public void run() {
			try {
				// allow for remaining cleanup
				Thread.sleep(1000);
			} catch (Throwable t) {
			} finally {
				// allow other shared providers to quit
				if (tally <= 0) {
					System.exit(0);
				}
			}
		}
	}

	/**
	 * Unexport the service provider appropriately.
	 * 
	 * @param force
	 *            terminate in progress calls if necessary
	 * @return true if unexport succeeds
	 */
	boolean unexport(boolean force) throws NoSuchObjectException {
		return delegate.unexport(force);
	}

	/**
	 * Returns a proxy object for this object. This value should not be null.
	 * Implements the <code>ServiceProxyAccessor</code> interface.
	 * 
	 * @return a proxy object reference
	 * @exception RemoteException
	 */
	public Object getServiceProxy() throws RemoteException {
		return getProxy();
	}

	/**
	 * Returns a proxy object for this provider. If the smart proxy is alocated
	 * then returns a non exported object to be registerd with loookup services.
	 * However, if a smart proxy implements {@link OuteProxy} then the
	 * provider's proxy is set as its inner proxy. Otherwise the {@link Remote}
	 * outer proxy of this provider is returned.
	 * 
	 * @return a proxy, or null
	 * @see sorcer.base.Provider#getProxy()
	 */
	public Object getProxy() {
		return delegate.getProxy();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see sorcer.core.provider.OuterProxy#getInnerProxy()
	 */
	public Remote getInner() {
		return delegate.getInner();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see sorcer.core.provider.OuterProxy#setInnerProxy(java.rmi.Remote)
	 */
	public void setInner(Object innerProxy) throws ProviderException {
		delegate.setInner(innerProxy);
	}

	/**
	 * Returns a string representation of this service provider.
	 * 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		String className = getClass().getName();
		className = className.substring(className.lastIndexOf('.') + 1);
		return className + "[" + delegate.getServiceID() + "]";
	}

	/**
	 * Simple container for an alternative return a value so we can provide more
	 * detailed diagnostics.
	 */
	class InitException extends Exception {
		private static final long serialVersionUID = 1;

		private InitException(String message, Throwable nested) {
			super(message, nested);
		}
	}

	/**
	 * Portion of construction that is common between the activatable and not
	 * activatable cases. This method performs the minimum number of operations
	 * before establishing the Subject, and logs errors.
	 */
	public void init(String[] configOptions, LifeCycle lifeCycle)
			throws Exception {
		logger.entering(this.getClass().toString(), "init");
		this.lifeCycle = lifeCycle;
		try {
			// Take the login context entry from the configuration file, if this
			// entry is null, server will start without a subject
			loginContext = (LoginContext) delegate.getJiniConfig().getEntry(
					PROVIDER, "loginContext", LoginContext.class, null);
			logger.finer("loginContext " + loginContext);
			if (loginContext == null) {
				logger.finer("Login Context was null when the service was Started");
				// Starting the Service with NO subject provided
				initAsSubject();
			} else {
				logger.finer("Login Context was not null when the service was Started");
				loginContext.login();
				logger.finer("Login Context subject= "
						+ loginContext.getSubject());

				try {
					// Starting the Service with a subject
					Subject.doAsPrivileged(loginContext.getSubject(),
							new PrivilegedExceptionAction() {
								public Object run() throws Exception {
									initAsSubject();
									return null;
								}
							}, null);
				} catch (PrivilegedActionException e) {
					logger.warning("######## Priviledged Exception Occured ########");
					throw e.getCause();
				}
			}
			logger.log(Level.INFO, "Provider service started: "
					+ getProviderName(), this);
		} catch (Throwable e) {
			initFailed(e);
		}
	}

	/**
	 * Log information about failing to initialize the service and rethrow the
	 * appropriate exception.
	 * 
	 * @param the
	 *            exception produced by the failure
	 */
	static void initFailed(Throwable e) throws Exception {
		String message = null;
		if (e instanceof InitException) {
			message = e.getMessage();
			e = e.getCause();
		}
		if (logger.isLoggable(Level.SEVERE)) {
			if (message != null) {
				logThrow(Level.SEVERE, "initFailed",
						"Unable to start provider service: {0}",
						new Object[] { message }, e);
			} else {
				logger.log(Level.SEVERE, "Unable to start provider service", e);
			}
		}
		if (e instanceof Exception) {
			throw (Exception) e;
		} else if (e instanceof Error) {
			throw (Error) e;
		} else {
			IllegalStateException ise = new IllegalStateException(
					e.getMessage());
			ise.initCause(e);
			throw ise;
		}
	}

	/** Logs a throw */
	private static void logThrow(Level level, String method, String msg,
			Object[] msgParams, Throwable t) {
		LogRecord r = new LogRecord(level, msg);
		r.setLoggerName(logger.getName());
		r.setSourceClassName(Provider.class.getName());
		r.setSourceMethodName(method);
		r.setParameters(msgParams);
		r.setThrown(t);
		logger.log(r);
	}

	/**
	 * Common construction for activatable and non-activatable cases, run under
	 * the proper Subject. If used, provider properties file has to declared as
	 * "properties" in the provider's Jini configuration file. However
	 * provider's properties can be defined directly in Jini provider's
	 * configuration file - the latter recommended.
	 * 
	 * @param config
	 *            Jini configuration
	 * @throws Exception
	 */
	private void initAsSubject() {
		boolean done = false;
		// Initialize all properties of a provider, from provider properties
		// file and from provider's Jini configuration file
		try {
			delegate.init(this);
			// Use locators specified in the Jini configuration file, otherwise
			// from the environment configuration
			// String[] lookupLocators = (String[]) Config.getNonNullEntry(
			// delegate.getJiniConfig(), PROVIDER, "lookupLocators",
			// String[].class, new String[] {});
			String[] lookupLocators = new String[] {};
			String locators = delegate.getProviderConfig().getProperty(
					P_LOCATORS);
			if (locators != null && locators.length() > 0) {
				lookupLocators = SorcerUtil.getTokens(locators, " ,");
			}
			logger.finer("provider lookup locators: "
					+ (lookupLocators.length == 0 ? "no locators" : Arrays
							.toString(lookupLocators)));

			String[] lookupGroups = (String[]) Config.getNonNullEntry(
					delegate.getJiniConfig(), PROVIDER, "lookupGroups",
					String[].class, new String[] {});
			if (lookupGroups.length == 0)
				lookupGroups = DiscoveryGroupManagement.ALL_GROUPS;
			logger.finer("provider lookup groups: "
					+ (lookupGroups != null ? "all groups" : Arrays
							.toString(lookupGroups)));

			String[] accessorGroups = (String[]) Config.getNonNullEntry(
					delegate.getJiniConfig(), PROVIDER, "accessorGroups",
					String[].class, new String[] {});
			if (accessorGroups.length == 0)
				accessorGroups = lookupGroups;
			logger.finer("service accessor groups: "
					+ (accessorGroups != null ? "all groups" : Arrays
							.toString(accessorGroups)));

			Entry[] serviceAttributes = getAttributes();
			serviceAttributes = addServiceUIDesciptors(serviceAttributes);

			logger.finer("service attributes: "
					+ Arrays.toString(serviceAttributes));
			ServiceID sid = getProviderID();
			if (sid != null) {
				delegate.setServerUuid(sid);
			} else {
				logger.fine("Provider does not provide ServiceID, using default");
			}
			logger.fine("ServiceID: " + delegate.getServiceID());

			LookupLocator[] locs = new LookupLocator[lookupLocators.length];
			for (int i = 0; i < locs.length; i++) {
				locs[i] = new LookupLocator(lookupLocators[i]);
			}
			// Make sure to turn off multicast discovery if requested
			String[] groups;
			if (Sorcer.isMulticastEnabled()) {
				if (lookupGroups != null && lookupGroups.length > 0)
					groups = lookupGroups;
				else
					groups = delegate.groupsToDiscover;
				logger.warning(">>>> USING MULTICAST");
			} else {
				groups = LookupDiscoveryManager.NO_GROUPS;
				logger.warning(">>>> USING UNICAST ONLY");
			}

			logger.info(">>>LookupDiscoveryManager with groups: "
					+ Arrays.toString(groups) + "\nlocators: "
					+ Arrays.toString(locs));
			ldmgr = new LookupDiscoveryManager(groups, locs, null);
			logger.info("*** PROXY>>>>>registering proxy for: "
					+ getProviderName() + ":" + getProxy());
			joinManager = new JoinManager(getProxy(), serviceAttributes, sid,
					ldmgr, null);
			done = true;
		} catch (Throwable e) {
			logger.warning("Error initializing service: " + e.getMessage());
			logger.throwing(ServiceProvider.class.getName(), "initAsSubject", e);
		} finally {
			if (!done) {
				try {
					unexport(true);
					return;
				} catch (Exception e) {
					logger.log(Level.INFO,
							"unable to unexport after failure during startup",
							e);
					return;
				}
			}
		}
	}

	/** A trust verifier for secure dynamic and smart proxies. */
	final static class ProxyVerifier implements TrustVerifier, Serializable {
		private final RemoteMethodControl serverProxy;

		private final Uuid serverUuid;

		/**
		 * Create the verifier, throwing UnsupportedOperationException if the
		 * server proxy does not implement both RemoteMethodControl and
		 * TrustEquivalence.
		 */
		public ProxyVerifier(Object serverProxy, Uuid serverUuid) {
			if (serverProxy instanceof RemoteMethodControl
					&& serverProxy instanceof TrustEquivalence) {
				this.serverProxy = (RemoteMethodControl) serverProxy;
			} else {
				throw new UnsupportedOperationException();
			}
			this.serverUuid = serverUuid;
		}

		/** Implement TrustVerifier */
		public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
				throws RemoteException {
			if (obj == null || ctx == null) {
				throw new NullPointerException();
			} else if (!(obj instanceof ProxyAccessor)) {
				return false;
			} else if (!(obj instanceof ReferentUuid))
				return false;

			if (!serverUuid.equals(((ReferentUuid) obj).getReferentUuid()))
				return false;

			RemoteMethodControl otherServerProxy = (RemoteMethodControl) ((ProxyAccessor) obj)
					.getProxy();
			MethodConstraints mc = otherServerProxy.getConstraints();
			TrustEquivalence trusted = (TrustEquivalence) serverProxy
					.setConstraints(mc);
			return trusted.checkTrustEquivalence(otherServerProxy);
		}
	}

	/**
	 * Returns a UI descriptor for this provider to be included in a UI
	 * descriptor for your provider. This method should be implemented in
	 * sublcasses inmplementing the Jini ServiceUI framwork.
	 * 
	 * @return an UI descriptor for your provider. <code>null</code> if not
	 *         overwritten in subclasses.
	 */
	public UIDescriptor getMainUIDescriptor() {
		return null;
	}

	public static UIDescriptor getProviderUIDescriptor() {
		UIDescriptor descriptor = null;
		try {
			descriptor = UIDescriptorFactory.getUIDescriptor(
					MainUI.ROLE,
					new UIComponentFactory(new URL[] { new URL(Sorcer
							.getWebsterUrl() + "/provider-ui.jar") },
							ProviderUI.class.getName()));
		} catch (Exception ex) {
			logger.throwing(ServiceProvider.class.getName(), "getServiceUI", ex);
		}
		return descriptor;
	}

	/**
	 * Returns an array of additional service UI descriptors to be included in a
	 * Jini service item that is registered with lookup services. By default a
	 * generic ServiceProvider service UI is provided with: attribute viewer,
	 * context and task editor for this service provider.
	 * 
	 * @return an array of service UI descriptors
	 */
	public UIDescriptor[] getServiceUIEntries() {
//		UIDescriptor uiDesc1 = null;
//		try {
//			uiDesc1 = UIDescriptorFactory.getUIDescriptor(
//					MainUI.ROLE,
//					new UIComponentFactory(new URL[] { new URL(Sorcer
//							.getWebsterUrl() + "/exertlet-ui.jar") },
//							NetletEditor.class.getName()));
//		} catch (Exception ex) {
//			logger.throwing(ServiceProvider.class.getName(), "getServiceUI", ex);
//		}

		UIDescriptor uiDesc2 = null;
		try {
			URL uiUrl = new URL(Sorcer.getWebsterUrl() + "/exertlet-ui.jar");
			URL helpUrl = new URL(Sorcer.getWebsterUrl()
					+ "/exertlet/exertlet-ui.html");

			// URL exportUrl, String className, String name, String helpFilename
			uiDesc2 = UIDescriptorFactory.getUIDescriptor(MainUI.ROLE,
					(JFrameFactory) new UIFrameFactory(new URL[] { uiUrl },
							NetletUI.class.getName(), "Exertlet Editor",
							helpUrl));
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		//return new UIDescriptor[] { getProviderUIDescriptor(), uiDesc1, uiDesc2 };
		return new UIDescriptor[] { getProviderUIDescriptor(), uiDesc2 };
	}

	/**
	 * Returns an appended list of entries that includes UI descriptors of this
	 * provider.
	 * 
	 * @param serviceAttributes
	 * @return an array of UI descriptors
	 */
	private Entry[] addServiceUIDesciptors(Entry[] serviceAttributes) {
		if (delegate.getSmartProxy() != null || delegate.getPartner() != null) {
			return serviceAttributes;
		}

		Entry[] attrs = serviceAttributes;
		Entry uiDescriptor = getMainUIDescriptor();
		UIDescriptor[] uiDescriptors = getServiceUIEntries();
		int tally = 0;
		if (uiDescriptor != null)
			tally++;
		if (uiDescriptors != null)
			tally = tally + uiDescriptors.length;
		if (tally == 0)
			return attrs;
		attrs = new Entry[serviceAttributes.length + tally];
		System.arraycopy(serviceAttributes, 0, attrs, 0,
				serviceAttributes.length);
		if (uiDescriptors != null)
			for (int i = 0; i < uiDescriptors.length; i++)
				attrs[serviceAttributes.length + i] = uiDescriptors[i];

		if (uiDescriptor != null)
			attrs[serviceAttributes.length + tally - 1] = uiDescriptor;
		return attrs;
	}

	public Map<?, ?> getServiceComponents() {
		return delegate.getServiceComponents();
	}

	public void setServiceComponents(Map<?, ?> serviceComponents) {
		delegate.setServiceComponents(serviceComponents);
	}

	public boolean isSpaceSecurityEnabled() {
		return delegate.isSpaceSecurityEnabled();
	}

	public boolean isMonitorable() {
		return delegate.isMonitorable();
	}

	public Context<?> getContext(Context<?> context) throws RemoteException {
		// TODO can be extended for finding service type and its selector in the
		// context parameter
		try {
			context.putValue(ContextManagement.CONTEXT_REQUEST_PATH,
					getContext());
		} catch (ContextException e) {
			e.printStackTrace();
		}

		return context;
	}

	public Logger getContextLogger() {
		return delegate.getContextLogger();
	}

	public Logger getProviderLogger() {
		return delegate.getProviderLogger();
	}

	public Logger getRemoteLogger() {
		return delegate.getRemoteLogger();
	}

	/**
	 * Defines rediness of the provider: true if this provider is ready to
	 * process the incoming exertion, otherwise false.
	 * 
	 * @return true if the provider is redy to execute the exertion
	 */
	public boolean isReady(Exertion exertion) {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see sorcer.core.context.ContextManagement#getContextScript()
	 */
	@Override
	public String getContextScript() throws RemoteException {
		// implement context management in subcllases
		ContextManagement contextManager = delegate.getContextManager();
		if (contextManager != null)
			return contextManager.getContextScript();
		else
			return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see sorcer.core.context.ContextManagement#getContext()
	 */
	@Override
	public Context<?> getContext() throws RemoteException {
		// implement context management in subcllases
		ContextManagement contextManager = delegate.getContextManager();
		if (contextManager != null)
			return contextManager.getContext();
		else
			return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * sorcer.core.context.ContextManagement#getMethodContextScript(java.lang
	 * .String, java.lang.String)
	 */
	@Override
	public String getMethodContextScript(String interfaceName, String methodName)
			throws RemoteException {
		ContextManagement contextManager = delegate.getContextManager();
		if (contextManager != null)
			return contextManager.getMethodContextScript(interfaceName,
					methodName);
		else
			return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * sorcer.core.context.ContextManagement#currentContextList(java.lang.String
	 * )
	 */
	@Override
	public String[] currentContextList(String interfaceName)
			throws RemoteException {
		ContextManagement contextManager = delegate.getContextManager();
		if (contextManager != null)
			return contextManager.currentContextList(interfaceName);
		else {
			return providerCurrentContextList(interfaceName);
		}
	}

	private String[] providerCurrentContextList(String interfaceName)
			throws RemoteException {
		boolean contextLoaded = false;
		try {
			FileInputStream fis = new FileInputStream("../configs/"
					+ ContextManagement.CONTEXT_FILENAME);
			ObjectInputStream in = new ObjectInputStream(fis);
			try {
				theContextMap = (HashMap<String, Context>) in.readObject();
			} catch (ClassNotFoundException e) {
				logger.throwing(this.getClass().getName(),
						"currentContextList", e);
			}
			in.close();
			fis.close();
			contextLoaded = true;
		} catch (IOException e) {
			logger.throwing(this.getClass().getName(), "currentContextList", e);
			contextLoaded = false;
		}
		String[] toReturn = new String[0];
		if (contextLoaded) {
			Set<String> keys = theContextMap.keySet();
			String[] temp = new String[keys.size()];

			int j = 0;
			for (Iterator<String> iter = keys.iterator(); iter.hasNext();) {
				temp[j] = iter.next();
				j++;
			}

			int counter = 0;
			for (int i = 0; i < temp.length; i++)
				if (temp[i].startsWith(interfaceName))
					counter++;
			toReturn = new String[counter];
			counter = 0;
			for (int i = 0; i < temp.length; i++)
				if (temp[i].startsWith(interfaceName)) {
					toReturn[counter] = temp[i].substring(interfaceName
							.length() + 2);
					counter++;
				}
		}
		return toReturn;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * sorcer.core.context.ContextManagement#deleteContext(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public boolean deleteContext(String interfaceName, String methodName)
			throws RemoteException {
		ContextManagement contextManager = delegate.getContextManager();
		if (contextManager != null)
			return contextManager.deleteContext(interfaceName, methodName);
		else {
			return providerDeleteContext(interfaceName, methodName);
		}
	}

	private boolean providerDeleteContext(String interfaceName,
			String methodName) throws RemoteException {
		boolean contextLoaded = false;
		try {
			FileInputStream fis = new FileInputStream("../configs/"
					+ ContextManagement.CONTEXT_FILENAME);
			ObjectInputStream in = new ObjectInputStream(fis);
			try {
				theContextMap = (HashMap<String, Context>) in.readObject();
			} catch (ClassNotFoundException e) {
				logger.throwing(this.getClass().getName(), "deleteContext", e);
			}
			in.close();
			contextLoaded = true;
		} catch (IOException e) {
			logger.throwing(this.getClass().getName(), "deleteContext", e);
			contextLoaded = false;
		}

		try {
			if (theContextMap.containsKey(interfaceName + ".." + methodName))
				theContextMap.remove(interfaceName + ".." + methodName);
			// theContextMap.put(interfaceName+".."+methodName, theContext);

			FileOutputStream fos = new FileOutputStream("../configs/"
					+ ContextManagement.CONTEXT_FILENAME);
			ObjectOutputStream out = new ObjectOutputStream(fos);
			out.writeObject(theContextMap);
			out.close();
			fos.close();
		} catch (IOException e) {
			logger.throwing(this.getClass().getName(), "deleteContext", e);
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * sorcer.core.context.ContextManagement#getMethodContext(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public Context<?> getMethodContext(String interfaceName, String methodName)
			throws RemoteException {
		ContextManagement contextManager = delegate.getContextManager();
		if (contextManager != null)
			return contextManager.getMethodContext(interfaceName, methodName);
		else {
			return providerGetMethodContext(interfaceName, methodName);
		}
	}

	private Context<?> providerGetMethodContext(String interfaceName,
			String methodName) throws RemoteException {
		logger.info("user directory is " + System.getProperty("user.dir"));
		boolean contextLoaded = false;
		try {
			FileInputStream fis = new FileInputStream("../configs/"
					+ ContextManagement.CONTEXT_FILENAME);
			ObjectInputStream in = new ObjectInputStream(fis);
			try {
				theContextMap = (HashMap<String, Context>) in.readObject();
			} catch (ClassNotFoundException e) {
				logger.throwing(this.getClass().getName(), "getMethodContext",
						e);
			}
			in.close();
			fis.close();
			contextLoaded = true;
		} catch (IOException ioe) {
			// logger.throwing(this.getClass().getName(), "getMethodContext",
			// ioe);
			// logger.info("no context file availabe for the provider: " +
			// getProviderName());
			contextLoaded = false;
		}
		Context context = new ServiceContext();
		if (contextLoaded
				&& theContextMap.containsKey(interfaceName + ".." + methodName)) {
			context = theContextMap.get(interfaceName + ".." + methodName);
		}
		return context;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * sorcer.core.context.ContextManagement#saveMethodContext(java.lang.String,
	 * java.lang.String, sorcer.service.Context)
	 */
	@Override
	public boolean saveMethodContext(String interfaceName, String methodName,
			Context theContext) throws RemoteException {
		ContextManagement contextManager = delegate.getContextManager();
		if (contextManager != null)
			return contextManager.saveMethodContext(interfaceName, methodName,
					theContext);
		else {
			return providerSaveMethodContext(interfaceName, methodName,
					theContext);
		}
	}

	private boolean providerSaveMethodContext(String interfaceName,
			String methodName, Context<?> theContext) throws RemoteException {
		boolean contextLoaded = false;
		try {
			FileInputStream fis = new FileInputStream("../configs/"
					+ ContextManagement.CONTEXT_FILENAME);
			ObjectInputStream in = new ObjectInputStream(fis);
			try {
				theContextMap = (HashMap<String, Context>) in.readObject();
			} catch (ClassNotFoundException e) {
				logger.throwing(this.getClass().getName(), "saveMethodContext",
						e);
			}
			in.close();
			contextLoaded = true;
		} catch (IOException e) {
			logger.throwing(this.getClass().getName(), "saveMethodContext", e);
			contextLoaded = false;
		}
		theContextMap.put(interfaceName + ".." + methodName, theContext);
		try {

			FileOutputStream fos = new FileOutputStream("../configs/"
					+ ContextManagement.CONTEXT_FILENAME);
			ObjectOutputStream out = new ObjectOutputStream(fos);
			out.writeObject(theContextMap);
			out.close();
			fos.close();
		} catch (IOException e) {
			logger.throwing(this.getClass().getName(), "put", e);
			return false;
		}
		return true;
	}

	public String[] getAccessorGroups() {
		return accessorGroups;
	}

	public void setAccessorGroups(String[] accessorGroups) {
		this.accessorGroups = accessorGroups;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see sorcer.core.Provider#mutualExclusion()
	 */
	@Override
	public boolean mutualExclusion() throws RemoteException {
		return delegate.mutualExclusion;
	}

	protected synchronized void doTimeKeeping(double callTimeSec) {
		totalCallTime += callTimeSec;
		avgExecTime = totalCallTime / numCalls;
		logger.info("execution time = " + (callTimeSec) + " [s]");
		logger.info("average execution time = " + (avgExecTime) + " [s]");
	}

	// fields for thread metrics
	//
	private int numThreads = 0;
	private ArrayList<String> threadIds = new ArrayList<String>();
	private int numCalls = 0;
	private double avgExecTime = 0;
	private double totalCallTime = 0;

	protected synchronized String doThreadMonitor(String serviceIdString) {
		String prefix;
		if (serviceIdString == null) {
			numCalls++;
			numThreads++;
			prefix = "adding thread";
			serviceIdString = new Integer(numCalls).toString();
			threadIds.add(serviceIdString);
		} else {
			numThreads--;
			prefix = "subtracting thread";
			threadIds.remove(serviceIdString);
		}
		logger.info("\n\n***provider class = " + this.getClass()
				+ "\n***" + prefix + ": total calls = " + numCalls
				+ "\n***" + prefix + ": number of threads running = "
				+ numThreads + "\n***" + prefix + ": thread ids running = "
				+ threadIds);

		return serviceIdString;
	}

	public void init() throws RemoteException {
		delegate.init(this);
	}

	public void init(String propFile) throws RemoteException {
		delegate.init(this, propFile);
	}

	public void init(ProviderDelegate delegate) {
		this.delegate = delegate;
	}

	public ServiceID getProviderID() throws RemoteException {
		return delegate.getServiceID();
	}

	/**
	 * Implements {@link net.jini.lookup.ServiceIDListener}.
	 * <p>
	 * This function is called when a service ID is assigned. It also tries to
	 * persist the service ID.
	 * <p>
	 * TODO: This functionality is similar / identical / linked to
	 * {@link ProviderDelegate#restore()}. Investigate.
	 * 
	 * @param sid
	 *            The assigned ServiceID
	 * 
	 * @see net.jini.lookup.ServiceIDListener#serviceIDNotify(net.jini.core.lookup.ServiceID)
	 */
	public void serviceIDNotify(ServiceID sid) {
		logger.info("Service has been assigned service ID: " + sid.toString());
		delegate.setServerUuid(sid);
		try {
			// args ->fileName, object, isAbsolutePath
			String fileName = getServiceIDFile();
			File file = new File(fileName);
			if (!file.exists())
				file.createNewFile();
			ObjectLogger.persist(fileName, sid, true);
		} catch (Exception e) {
			// e.printStackTrace();
			logger.info("Cannot write service ID to persistent storage. So writting to present directory");
			try {
				File file = new File("provider.sid");
				if (!file.exists())
					file.createNewFile();
				ObjectLogger.persist("provider.sid", sid, true);
			} catch (Exception ex) {
			}
		}
	}

	/**
	 * Returns a server delegate for this server.
	 * 
	 * @return this server delegate
	 */
	public ProviderDelegate getDelegate() {
		return delegate;
	}

	private String getServiceIDFile() {
		String packagePath = this.getClass().getName();
		packagePath = packagePath.substring(0, packagePath.lastIndexOf("."))
				.replace('.', File.separatorChar);
		String sidFile = new StringBuffer(Sorcer.getWebsterUrl())
				.append(File.separatorChar)
				.append(packagePath)
				.append(packagePath.substring(packagePath
						.lastIndexOf(File.separatorChar))).append(".sid")
				.toString();
		return sidFile;
	}

	public long getLeastSignificantBits() throws RemoteException {
		return (delegate.getServiceID() == null) ? -1 : delegate.getServiceID()
				.getLeastSignificantBits();
	}

	public long getMostSignificantBits() throws RemoteException {
		return (delegate.getServiceID() == null) ? -1 : delegate.getServiceID()
				.getMostSignificantBits();
	}

	/**
	 * A provider responsibility is to check a task completeness in paricular
	 * the relevance of the task's context.
	 */
	public boolean isValidTask(Exertion task) throws RemoteException,
			ExertionException {
		return true;
	}

	public String getInfo() throws RemoteException {
		return SorcerUtil.arrayToString(getAttributes());
	}

	public boolean isValidMethod(String name) throws RemoteException,
			SignatureException {
		return delegate.isValidMethod(name);
	}

	public Context invokeMethod(String method, Context context)
			throws ExertionException {
		return delegate.invokeMethod(method, context);
	}

	public Exertion invokeMethod(String methodName, Exertion ex)
			throws ExertionException {
		return delegate.invokeMethod(methodName, ex);
	}

	/**
	 * This method calls on an ExertionProcessor which executes the exertion
	 * accordingly to its compositional type.
	 * 
	 * @param exertion
	 *            Exertion
	 * @return Exertion
	 * @throws ExertionException
	 * @see Exertion
	 * @see Conditional
	 * @see ExertProcessor
	 * @throws RemoteException
	 * @throws ExertionException
	 */
	public Exertion doExertion(final Exertion exertion, Transaction txn)
			throws ExertionException {
		// create an instance of the ExertionProcessor and call on the
		// process method, returns an Exertion
		ExertProcessor ep = null;
		if (this instanceof Jobber) {
			ep = new ExertProcessor(exertion, delegate, (Jobber) this);
		} else if (this instanceof Spacer) {
			ep = new ExertProcessor(exertion, delegate, (Spacer) this);
		} else if (exertion instanceof Task && exertion.isMonitored()) {
			if (exertion.isWait())
				return doMonitoredTask(exertion, null);
			else {
				// asynchronous execution
				Thread dt = new Thread(new Runnable() {
					public void run() {
						doMonitoredTask(exertion, null);
					}
				});
				try {
					exertion.getContext().putValue(
							ControlContext.EXERTION_WAIT_FROM,
							SorcerUtil.getDateTime());
				} catch (ContextException e) {
					// ignore it
					e.printStackTrace();
				}
				dt.start();
				return exertion;
			}
		} else {
			ep = new ExertProcessor(exertion, delegate);
		}
		return ep.process(threadManager);
	}

	public Exertion service(Exertion exertion) throws RemoteException,
			ExertionException {
		return doExertion(exertion, null);
	}

	public Exertion service(Exertion exertion, Transaction txn)
			throws RemoteException {
		// TODO transaction handling to be implemented when needed
		// TO DO HANDLING SUSSPENDED exertions
		// if (((ServiceExertion) exertion).monitorSession != null) {
		// new Thread(new ServiceThread(exertion, this)).start();
		// return exertion;
		// }

		// when service Locker is used
		if (delegate.mutualExlusion()) {
			Object mutexId = exertion.getControlContext().getMutexId();
			if (mutexId == null) {
				exertion.getControlContext().appendTrace(
						"mutex required by: " + getProviderName() + ":"
								+ getProviderID());
				return exertion;
			} else if (!(mutexId.equals(delegate.getServiceID()))) {
				exertion.getControlContext().appendTrace(
						"invalid mutex for: " + getProviderName() + ":"
								+ getProviderID());
				return exertion;
			}
		}
		// allow provider to leave a trace
		// exertion.getControlContext().appendTrace(
		// delegate.mutualExlusion() ? "mutex in: "
		// + getProviderName() + ":" + getProviderID()
		// : "in: " + getProviderName() + ":"
		// + getProviderID());
		Exertion out = exertion;
		try {
			out = doExertion(exertion, txn);
		} catch (ExertionException e) {
			e.printStackTrace();
			((ServiceExertion) out).reportException(new ExertionException(
					getProviderName() + " failed", e));
		}
		return out;
	}

	public ServiceExertion dropTask(ServiceExertion task)
			throws RemoteException, ExertionException, SignatureException {
		return delegate.dropTask(task);
	}

	public Job dropJob(Job job) throws RemoteException, ExertionException {
		return delegate.dropJob(job);
	}

	public Entry[] getAttributes() throws RemoteException {
		return delegate.getAttributes();
	}

	public List<Object> getProperties() throws RemoteException {
		return delegate.getProperties();
	}

	public Properties getProviderProperties() throws RemoteException {
		return delegate.getProviderProperties();
	}

	public Configuration getProviderConfiguration() {
		return delegate.getProviderConfiguration();
	}

	public String getDescription() throws RemoteException {
		return delegate.getDescription();
	}

	public String[] getGroups() throws RemoteException {
		return delegate.getGroups();
	}

	public String getProviderName() throws RemoteException {
		return delegate.getProviderName();
	}

	public void restore() throws RemoteException {
		delegate.restore();
	}

	public void fireEvent() throws RemoteException {
		// do noting
	}

	public void loadConfiguration(String filename) throws RemoteException {
		delegate.getProviderConfig().loadConfiguration(filename);
	}

	/** for a testing purpose only. */
	public void hangup() throws RemoteException {
		delegate.hangup();
	}

//	public void quit() throws InterruptedException {
//		// jm.terminate();
//		Thread.sleep(1000);
//		System.exit(0);
//	}

	public File getScratchDir() {
		return delegate.getScratchDir();
	}

	// this methods generates a scratch directory with a prefix on the unique
	// directory name
	// and puts the directory into the context under a fixed path (path is in
	// SorcerConstants)
	public File getScratchDir(Context context, String scratchDirNamePrefix)
			throws RemoteException {
		File scratchDir;
		try {
			scratchDir = delegate.getScratchDir(context, scratchDirNamePrefix);
		} catch (Exception e) {
			throw new RemoteException("***error: problem getting scratch "
					+ "directory and adding path/url to context"
					+ "\ncontext name = " + context.getName() + "\ncontext = "
					+ context + "\nscratchDirNamePrefix = "
					+ scratchDirNamePrefix + "\nexception = " + e.toString());
		}
		return scratchDir;
	}

	// method adds scratch dir to context
	public File getScratchDir(Context context) throws RemoteException {
		return getScratchDir(context, "");
		// File scratchDir;
		// try {
		// scratchDir = delegate.getScratchDir(context);
		// } catch (Exception e) {
		// throw new RemoteException("***error: problem getting scratch "
		// + "directory and adding path/url to context, exception "
		// + "follows:\n" + e.toString());
		// }
		// return scratchDir;
	}

	public URL getScratchURL(File scratchFile) throws MalformedURLException {
		return delegate.getScratchURL(scratchFile);
	}

	public String getProperty(String key) {
		return delegate.getProviderConfig().getProperty(key);
	}

	public String getProperty(String key, String defaultValue) {
		return delegate.getProviderConfig().getProperty(key, defaultValue);
	}

	public void notifyInformation(Exertion task, String message)
			throws RemoteException {
		delegate.notifyInformation(task, message);
	}

	public void notifyException(Exertion task, String message, Exception e)
			throws RemoteException {
		delegate.notifyException(task, message, e);
	}

	public void notifyExceptionWithStackTrace(Exertion task, Exception e)
			throws RemoteException {
		delegate.notifyExceptionWithStackTrace(task, e);
	}

	public void notifyException(Exertion task, Exception e)
			throws RemoteException {
		delegate.notifyException(task, e);
	}

	public void notifyWarning(Exertion task, String message)
			throws RemoteException {
		delegate.notifyWarning(task, message);
	}

	public void notifyFailure(Exertion task, Exception e)
			throws RemoteException {
		delegate.notifyFailure(task, e);
	}

	public void notifyFailure(Exertion task, String message)
			throws RemoteException {
		delegate.notifyFailure(task, message);
	}

	// task/job monitoring API
	public void stop(UEID ref, Subject subject) throws RemoteException,
			UnknownExertionException, AccessDeniedException {
		delegate.stop(ref, subject);
	}

	/**
	 * MonitorManagers call suspend a MonitorableServicer. Once suspend is
	 * called, the monitorables must suspend immediatly and return the suspended
	 * state of the context.
	 * 
	 * @throws UnknownExertionException
	 *             if the exertion is not executed by this provider.
	 * 
	 * @throws RemoteException
	 *             if there is a communication error
	 * 
	 */

	public void suspend(UEID ref, Subject subject) throws RemoteException,
			UnknownExertionException, AccessDeniedException {
		delegate.suspend(ref, subject);
	}

	/**
	 * Resume if the resume functionality is supported by the provider Else
	 * start from the begining.
	 * 
	 * @throws UnknownExertionException
	 *             if the exertion is not executed by this provider.
	 * 
	 * @throws RemoteException
	 *             if there is a communication error
	 * 
	 */
	public void resume(Exertion ex) throws RemoteException, ExertionException {
		service(ex);
	}

	/**
	 * Step if the step functionality is supported by the provider Else start
	 * from the begining.
	 * 
	 * @throws UnknownExertionException
	 *             if the exertion is not executed by this provider.
	 * 
	 * @throws RemoteException
	 *             if there is a communication error
	 * 
	 */
	public void step(Exertion ex) throws RemoteException, ExertionException {
		service(ex);
	}

	/**
	 * Calls the delegate to update the monitor with the current context.
	 * 
	 * @param context
	 * @throws MonitorException
	 * @throws RemoteException
	 */
	public void changed(Context<?> context, Object aspect) throws RemoteException,
			MonitorException {
		delegate.changed(context, aspect);
	}

	/**
	 * Destroy the service, if possible, including its persistent storage.
	 * 
	 * @see sorcer.base.Provider#destroy()
	 */
	public void destroy() throws RemoteException {
		logger.log(Level.INFO, "Destroying service " + getProviderName());
		joinManager.terminate();
		logger.finer("destroy provider >>> shared provider tally: " + tally);
		tally = tally - 1;
		// temp fix
		//delegate.destroy();
		// temp fix
		System.exit(0);
		unexport(true);
		if (lifeCycle != null) {
			lifeCycle.unregister(this);
		}
		new Thread(new DestroyThread()).start();
		delegate.setRunning(false);
	}
	
	/**
	 * Destroy all services in this node (virtual machine) by calling each
	 * destroy().
	 * 
	 * @see sorcer.base.Provider#destroy()
	 */
	public void destroyNode() throws RemoteException {
		for (ServiceProvider provider : providers) {
			provider.destroy();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/** {@inheritDoc} */
	public Uuid getReferentUuid() {
		return delegate.getServerUuid();
	}

	public void updatePolicy(Policy policy) throws RemoteException {
		if (Sorcer.getProperty("sorcer.policer.mandatory").equals("true")) {
			Policy.setPolicy(policy);
		} else {
			logger.info("sorcer.policer.mandatory property in sorcer.env is false");
		}
	}

	public HashMap<String, Context> theContextMap = new HashMap<String, Context>();

	public boolean loadContextDatabase() throws RemoteException {
		try {
			FileInputStream fis = new FileInputStream("context.cxnt");
			ObjectInputStream in = new ObjectInputStream(fis);
			try {
				theContextMap = (HashMap<String, Context>) in.readObject();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			in.close();
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	protected void setupThreadManager() {
		// TaskManger(int maxThreads, long timeout, float loadFactor)
		// 10, 1000 * 15, 3.0f
		Configuration config = delegate.getJiniConfig();
		int maxThreads = 10, waitIncrement = 50;
		long timeout = 1000 * 15;
		float loadFactor = 3.0f;
		boolean threadManagement = false;
		try {
			threadManagement = (Boolean) config.getEntry(
					ServiceProvider.COMPONENT, THREAD_MANAGEMNT, boolean.class,
					false);
		} catch (Exception e) {
			// do nothing, default value is used
			// e.printStackTrace();
		}
		logger.info("threadManagement: " + threadManagement);
		if (!threadManagement) {
			return;
		}

		try {
			maxThreads = (Integer) config.getEntry(ServiceProvider.COMPONENT,
					MAX_THREADS, int.class);
		} catch (Exception e) {
			logger.throwing(ServiceProvider.class.getName(),
					"setupThreadManger#maxThreads", e);
		}
		logger.info("maxThreads: " + maxThreads);
		try {
			timeout = (Long) config.getEntry(ServiceProvider.COMPONENT,
					MANAGER_TIMEOUT, long.class);
		} catch (Exception e) {
			logger.throwing(ServiceProvider.class.getName(),
					"setupThreadManger#timeout", e);
		}
		logger.info("timeout: " + timeout);
		try {
			loadFactor = (Float) config.getEntry(ServiceProvider.COMPONENT,
					LOAD_FACTOR, float.class);
		} catch (Exception e) {
			logger.throwing(ServiceProvider.class.getName(),
					"setupThreadManger#loadFactor", e);
		}
		logger.info("loadFactor: " + loadFactor);
		try {
			waitIncrement = (Integer) config.getEntry(
					ServiceProvider.COMPONENT, WAIT_INCREMENT, int.class,
					waitIncrement);
		} catch (Exception e) {
			logger.throwing(ServiceProvider.class.getName(),
					"setupThreadManger#waitIncrement", e);
		}
		logger.info("waitIncrement: " + waitIncrement);

		ExertProcessor.WAIT_INCREMENT = waitIncrement;

		threadManager = new TaskManager(maxThreads, timeout, loadFactor);
	}

	public final static String DB_HOME = "dbHome";
	public final static String THREAD_MANAGEMNT = "threadManagement";
	public final static String MAX_THREADS = "maxThreads";
	public final static String MANAGER_TIMEOUT = "threadTimeout";
	public final static String LOAD_FACTOR = "loadFactor";
	// wait for a TakThread result in increments
	public final static String WAIT_INCREMENT = "waitForResultIncrement";

	/*
	 * (non-Javadoc)
	 * 
	 * @see sorcer.core.Provider#getJavaSystemProperties()
	 */
	@Override
	public Properties getJavaSystemProperties() throws RemoteException {
		return System.getProperties();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see sorcer.core.Provider#isContextValid(sorcer.service.Context,
	 * sorcer.service.Signature)
	 */
	@Override
	public boolean isContextValid(Context<?> dataContext, Signature forSignature) {
		return true;
	}

	public Logger getLogger() {
		return logger;
	}

	private final int SLEEP_TIME = 250;

	public Exertion doMonitoredTask(Exertion task, Transaction txn) {
		MonitoredTaskDispatcher dispatcher = null;
		try {
			dispatcher = new MonitoredTaskDispatcher(task, null, false, this);
			// logger.finer("*** MonitoredTaskDispatcher started with control context ***\n"
			// + task.getControlContext());
			// int COUNT = 1000;
			// int count = COUNT;
			while (dispatcher.getState() != ExecState.DONE
					&& dispatcher.getState() != ExecState.FAILED
					&& dispatcher.getState() != ExecState.SUSPENDED) {
				// count--;
				// if (count < 0) {
				// logger.finer("*** MonitoredTaskDispatcher waiting in state: "
				// + dispatcher.getState());
				// count = COUNT;
				// }
				Thread.sleep(SLEEP_TIME);
			}
			// logger.finer("*** MonitoredTaskDispatcher exit state = "
			// + dispatcher.getState() + " for ***\n"
			// + task.getControlContext());
		} catch (DispatcherException de) {
			de.printStackTrace();
		} catch (InterruptedException ie) {
			ie.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return (Task) dispatcher.getExertion();
	}
	
}
