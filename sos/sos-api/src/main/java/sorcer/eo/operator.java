/**
 *
 * Copyright 2013 the original author or authors.
 * Copyright 2013 Sorcersoft.com S.A.
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
package sorcer.eo;

import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.transaction.Transaction;
import net.jini.id.Uuid;
import sorcer.co.tuple.*;
import sorcer.core.Provider;
import sorcer.core.SorcerConstants;
import sorcer.core.SorcerEnv;
import sorcer.core.StorageManagement;
import sorcer.core.context.*;
import sorcer.core.context.ControlContext.ThrowableTrace;
import sorcer.core.exertion.*;
import sorcer.core.signature.EvaluationSignature;
import sorcer.core.signature.NetSignature;
import sorcer.core.signature.ObjectSignature;
import sorcer.core.signature.ServiceSignature;
import sorcer.service.*;
import sorcer.service.Signature.Type;
import sorcer.service.Strategy.*;

import sorcer.util.bdb.SosURL;
import sorcer.util.bdb.objects.Store;
import sorcer.util.bdb.sdb.SdbUtil;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static sorcer.util.UnknownName.getUnknown;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class operator {

	private static final Logger logger = Logger.getLogger(operator.class
			.getName());

	public static String path(List<String> attributes) {
		if (attributes.size() == 0)
			return null;
		if (attributes.size() > 1) {
			StringBuilder spr = new StringBuilder();
			for (int i = 0; i < attributes.size() - 1; i++) {
				spr.append(attributes.get(i)).append(SorcerConstants.CPS);
			}
			spr.append(attributes.get(attributes.size() - 1));
			return spr.toString();
		}
		return attributes.get(0);
	}

	public static Object revalue(Evaluation evaluation, String path,
			Parameter... entries) throws ContextException {
		Object obj = value(evaluation, path, entries);
		if (obj instanceof Evaluation) {
			obj = value((Evaluation) obj, entries);
		}
		return obj;
	}

	public static Object revalue(Object object, Parameter... entries)
			throws EvaluationException {
		Object obj = null;
		if (object instanceof Evaluation) {
			obj = value((Evaluation) object, entries);
		}
		if (obj == null) {
			obj = object;
		} else {
			if (obj instanceof Evaluation) {
				obj = value((Evaluation) obj, entries);
			}
		}
		return obj;
	}

	public static String path(String... attributes) {
		if (attributes.length == 0)
			return null;
		if (attributes.length > 1) {
			StringBuilder spr = new StringBuilder();
			for (int i = 0; i < attributes.length - 1; i++) {
				spr.append(attributes[i]).append(SorcerConstants.CPS);
			}
			spr.append(attributes[attributes.length - 1]);
			return spr.toString();
		}
		return attributes[0];
	}

	public static Complement<String, Object> subject(String path, Object value)
			throws SignatureException {
		return new Complement<String, Object>(path, value);
	}

	public static <T extends Context> T put(T context, Tuple2... entries)
			throws ContextException {
		for (int i = 0; i < entries.length; i++) {
			if (context != null) {
				context.putValue(((Tuple2<String, ?>) entries[i])._1,
						((Tuple2<String, ?>) entries[i])._2);
			}
		}
		return context;
	}

	public static void put(Exertion exertion, Tuple2<String, ?>... entries)
			throws ContextException {
		put(exertion.getDataContext(), entries);
	}

	public static Exertion setContext(Exertion exertion, Context context) {
		((ServiceExertion) exertion).setContext(context);
		return exertion;
	}

	public static ControlContext control(Exertion exertion)
			throws ContextException {
		return exertion.getControlContext();
	}

	public static ControlContext control(Exertion exertion, String childName)
			throws ContextException {
		return exertion.getExertion(childName).getControlContext();
	}

	public static <T> Context cxt(T... entries)
			throws ContextException {
		return context(entries);
	}

	public static Context jobContext(Exertion job) throws ContextException {
		return ((Job) job).getJobContext();
	}

	public static DataEntry data(Object data) {
		return new DataEntry(Context.DSD_PATH, data);
	}

	public static <T> Context context(T... entries)
			throws ContextException {
        return ContextFactory.context(entries);
    }

	public static List<String> names(List<? extends Identifiable> list) {
		List<String> names = new ArrayList<String>(list.size());
		for (Identifiable i : list) {
			names.add(i.getName());
		}
		return names;
	}

	public static String name(Object identifiable) {
		if (identifiable instanceof Identifiable)
			return ((Identifiable) identifiable).getName();
		else
			return null;
    }

	public static List<String> names(Identifiable... array) {
		List<String> names = new ArrayList<String>(array.length);
		for (Identifiable i : array) {
			names.add(i.getName());
		}
		return names;
	}

	public static List<Entry> attributes(Entry... entries) {
		List<Entry> el = new ArrayList<Entry>(entries.length);
        Collections.addAll(el, entries);
		return el;
	}

	/**
	 * Makes this Revaluation revaluable, so its return value is to be again
	 * evaluated as well.
     *
	 * @return an revaluable Evaluation
	 * @throws EvaluationException
	 */
	public static Revaluation revaluable(Revaluation evaluation,
			Parameter... entries) throws EvaluationException {
		if (entries != null && entries.length > 0) {
			try {
				((Evaluation) evaluation).substitute(entries);
			} catch (RemoteException e) {
				throw new EvaluationException(e);
			}
		}
		evaluation.setRevaluable(true);
		return evaluation;
	}

	public static Revaluation unrevaluable(Revaluation evaluation) {
		evaluation.setRevaluable(false);
		return evaluation;
	}

	/**
	 * Returns the Evaluation with a realized substitution for its arguments.
	 * 
	 * @param evaluation
	 * @param entries
	 * @return an evaluation with a realized substitution
	 * @throws EvaluationException
	 * @throws RemoteException
	 */
	public static Evaluation substitute(Evaluation evaluation,
			Parameter... entries) throws EvaluationException, RemoteException {
		return evaluation.substitute(entries);
	}

	public static Signature sig(Class<?> serviceType, String providerName,
			Object... parameters) throws SignatureException {
		return sig(null, serviceType, providerName, parameters);
	}

	public static Signature sig(String operation, Class<?> serviceType,
			String providerName, Object... parameters)
			throws SignatureException {
        return SignatureFactory.sig(operation, serviceType, providerName, parameters);
    }

	public static Signature sig(String selector) throws SignatureException {
		return new ServiceSignature(selector);
	}

	public static Signature sig(String name, String selector)
			throws SignatureException {
		return new ServiceSignature(name, selector);
	}

	public static Signature sig(String operation, Class<?> serviceType,
			Type type) throws SignatureException {
		return sig(operation, serviceType, null, type);
	}

	public static Signature sig(String operation, Class serviceType) throws SignatureException {
		return SignatureFactory.sig(operation, serviceType);
	}

	public static Signature sig(String operation, Class<?> serviceType,
			Provision type) throws SignatureException {
		return sig(operation, serviceType, null, type);
	}

	public static Signature sig(String operation, Class<?> serviceType,
			List<net.jini.core.entry.Entry> attributes)
			throws SignatureException {
		NetSignature op = new NetSignature();
		op.setAttributes(attributes);
		op.setServiceType(serviceType);
		op.setSelector(operation);
		return op;
	}

	public static Signature sig(Class<?> serviceType) throws SignatureException {
		return sig(serviceType, null);
	}

	public static Signature sig(Class<?> serviceType, ReturnPath returnPath)
			throws SignatureException {
		Signature sig;
		if (serviceType.isInterface()) {
			sig = new NetSignature("service", serviceType);
		} else if (Executor.class.isAssignableFrom(serviceType)) {
			sig = new ObjectSignature("execute", serviceType);
		} else {
			sig = new ObjectSignature(serviceType);
		}
		if (returnPath != null)
			sig.setReturnPath(returnPath);
		return sig;
	}

	public static Signature sig(String operation, Class<?> serviceType,
			ReturnPath resultPath) throws SignatureException {
		Signature sig = sig(operation, serviceType, Type.SRV);
		sig.setReturnPath(resultPath);
		return sig;
	}

	public static Signature sig(Exertion exertion, String componentExertionName) {
		Exertion component = exertion.getExertion(componentExertionName);
		return component.getProcessSignature();
	}

	public static String selector(Signature sig) {
		return sig.getSelector();
	}

	public static Signature type(Signature signature, Signature.Type type) {
		signature.setType(type);
		return signature;
	}

	public static ObjectSignature sig(String operation, Object object,
			Class... types) throws SignatureException {
		try {
			if (object instanceof Class && ((Class) object).isInterface()) {
				return new NetSignature(operation, (Class) object);
			} else {
				return new ObjectSignature(operation, object,
						types.length == 0 ? null : types);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new SignatureException(e);
		}
	}

	public static ObjectSignature sig(String selector, Object object,
			Class<?>[] types, Object[] args) throws SignatureException {
		try {
			return new ObjectSignature(selector, object, types, args);
		} catch (Exception e) {
			e.printStackTrace();
			throw new SignatureException(e);
		}
	}

	public static ObjectTask task(ObjectSignature signature)
			throws SignatureException {
		return TaskFactory.task(signature);
	}

    public static Task task(String name, Signature signature, Context context)
            throws SignatureException {
        return TaskFactory.task(name, signature, context);
    }

	public static <T> Task batch(String name, T... elems)
			throws ExertionException {
		Task batch = task(name, elems);
		if (batch.getSignatures().size() > 1)
			return batch;
		else
			throw new ExertionException(
					"A batch task should comprise of more than one signature.");
	}

	public static <T> Task task(String name, T... elems)
			throws ExertionException {
        return TaskFactory.task(name, elems);
    }

	public static <T, E extends Exertion> E srv(String name,
			T... elems) throws ExertionException, ContextException,
			SignatureException {
		return (E) exertion(name, elems);
	}

	public static <T, E extends Exertion> E xrt(String name,
			T... elems) throws ExertionException, ContextException,
			SignatureException {
		return (E) exertion(name, elems);
	}

	public static <T, E extends Exertion> E exertion(
			String name, T... elems) throws ExertionException,
			ContextException, SignatureException {
		List<Exertion> exertions = new ArrayList<Exertion>();
		for (int i = 0; i < elems.length; i++) {
			if (elems[i] instanceof Exertion) {
				exertions.add((Exertion) elems[i]);
			}
		}
		if (exertions.size() > 0) {
			Job j = job(elems);
			j.setName(name);
			return (E) j;
		} else {
			Task t = task(name, elems);
			return (E) t;
		}
	}

	public static <T> Job job(T... elems) throws ExertionException,
			ContextException, SignatureException {
		String name = getUnknown();
		Signature signature = null;
		ControlContext control = null;
		Context<?> data = null;
		List<Exertion> exertions = new ArrayList<Exertion>();
		List<Pipe> pipes = new ArrayList<Pipe>();

		for (int i = 0; i < elems.length; i++) {
			if (elems[i] instanceof String) {
				name = (String) elems[i];
			} else if (elems[i] instanceof Exertion) {
				exertions.add((Exertion) elems[i]);
			} else if (elems[i] instanceof ControlContext) {
				control = (ControlContext) elems[i];
			} else if (elems[i] instanceof Context) {
				data = (Context<?>) elems[i];
			} else if (elems[i] instanceof Pipe) {
				pipes.add((Pipe) elems[i]);
			} else if (elems[i] instanceof Signature) {
				signature = ((Signature) elems[i]);
			}
		}

		Job job = null;
		if (signature == null) {
			signature = sig("service", Jobber.class);
		}
		if (signature instanceof NetSignature) {
			job = new NetJob(name);
		} else if (signature instanceof ObjectSignature) {
			job = new ObjectJob(name);
		}
		job.addSignature(signature);
		if (data != null)
			job.setContext(data);

		if (job instanceof NetJob && control != null) {
			job.setControlContext(control);
			if (control.getAccessType().equals(Access.PULL)) {
				Signature procSig = job.getProcessSignature();
				procSig.setServiceType(Spacer.class);
				job.getSignatures().clear();
				job.addSignature(procSig);
				if (data != null)
					job.setContext(data);
				else
					job.getDataContext().setExertion(job);
			}
		}
		if (exertions.size() > 0) {
			for (Exertion ex : exertions) {
				job.addExertion(ex);
			}
			for (Pipe p : pipes) {
				logger.finer("from dataContext: " + p.in.getDataContext().getName()
						+ " path: " + p.inPath);
				logger.finer("to dataContext: " + p.out.getDataContext().getName()
						+ " path: " + p.outPath);
				p.out.getDataContext().connect(p.outPath, p.inPath,
						p.in.getDataContext());
			}
		} else
			throw new ExertionException("No component exertion defined.");

		return job;
	}

	public static Object get(Context<?> context, String path)
			throws ContextException {
		return context.getValue(path);
	}

	public static Object get(Context context) throws ContextException {
		return context.getReturnValue();
	}

	public static Object get(Context context, int index)
			throws ContextException {
		if (context instanceof PositionalContext)
			return ((PositionalContext) context).getValueAt(index);
		else
			throw new ContextException("Not PositionalContext, index: " + index);
	}

	public static Object get(Exertion exertion) throws ContextException {
		return exertion.getContext().getReturnValue();
	}

	public static <V> V asis(Object evaluation) throws EvaluationException {
		if (evaluation instanceof Evaluation) {
			try {
				synchronized (evaluation) {
					return ((Evaluation<V>) evaluation).asis();
				}
			} catch (RemoteException e) {
				throw new EvaluationException(e);
			}
		} else {
			throw new EvaluationException(
					"asis value can only be determined for objects of the "
							+ Evaluation.class + " type");
		}
	}

	public static Object get(Exertion exertion, String component, String path)
			throws ExertionException {
		Exertion c = exertion.getExertion(component);
		return get(c, path);
	}

	public static Object value(URL url) throws IOException {
		return url.getContent();
	}

	public static Object value(SosURL url) throws IOException {
		return url.getTarget().getContent();
	}

	public static SosURL set(SosURL url, Object value)
			throws EvaluationException {
		URL target = url.getTarget();
		if (target != null && value != null) {
			try {
				if (target.getRef() == null) {
					url.setTarget(store(value));
				} else {
                    update(SdbUtil.getUuid(target), value);
                }
			} catch (Exception e) {
				throw new EvaluationException(e);
			}
		}
		return url;
	}

    public static <T> T value(Exertion evaluation, Parameter... entries) throws EvaluationException {
        try {
            synchronized (evaluation) {
                return (T) exec(evaluation, entries);
            }
        } catch (EvaluationException e) {
            throw e;
        } catch (Exception e) {
            throw new EvaluationException(e);
        }
    }

    public static <T> T value(Evaluation<T> evaluation, Parameter... entries)
			throws EvaluationException {
        return Evaluator.value(evaluation, entries);
    }

	public static <T> T value(Evaluation<T> evaluation, String evalSelector,
			Parameter... entries) throws EvaluationException {
		synchronized (evaluation) {
			if (evaluation instanceof Exertion) {
				try {
					return (T) exec((Exertion) evaluation, entries);
				} catch (Exception e) {
					e.printStackTrace();
					throw new EvaluationException(e);
				}
			} else if (evaluation instanceof Context) {
				try {
					return (T) ((Context) evaluation).getValue(evalSelector,
							entries);
				} catch (Exception e) {
					e.printStackTrace();
					throw new EvaluationException(e);
				}
			}
		}
		return null;
	}

	public static Object asis(Context context, String path)
			throws ContextException {
		return ((ServiceContext) context).getAsis(path);
	}

	public static List<Exertion> exertions(Exertion xrt) {
		return xrt.getAllExertions();
	}

	public static Exertion exertion(Exertion xrt, String componentExertionName) {
		return ((Job) xrt).getComponentExertion(componentExertionName);
	}

	public static List<String> trace(Exertion xrt) {
		return xrt.getControlContext().getTrace();
	}

	public static void print(Object obj) {
		System.out.println(obj.toString());
	}

	public static Object exec(Context context, Parameter... entries)
			throws ExertionException, ContextException {
        //TODO it appears it's unused
		try {
			context.substitute(entries);
		} catch (RemoteException e) {
			throw new ContextException(e);
		}
		ReturnPath returnPath = context.getReturnPath();
		if (returnPath != null) {
			return context.getValue(returnPath.path);
		} else
			throw new ExertionException("No return path in the dataContext: "
					+ context.getName());
	}

	public static Object exec(Exertion exertion, Parameter... entries)
			throws ExertionException, ContextException {
        return ExertionExecutor.exec(exertion, entries);
	}

	public static Object get(Exertion xrt, String path)
			throws ExertionException {
		try {
			return ((ServiceExertion) xrt).getValue(path);
		} catch (ContextException e) {
			throw new ExertionException(e);
		}
	}

	public static List<ThrowableTrace> exceptions(Exertion exertion) {
		return exertion.getExceptions();
	}

	public static Task exert(Task input, Entry... entries)
			throws ExertionException {
		try {
			return ((Task) input).exert(null, entries);
		} catch (Exception e) {
			throw new ExertionException(e);
		}
	}

	public static <T extends Exertion> T exert(Exertion input,
			Parameter... entries) throws ExertionException {
		try {
			return (T) exert(input, null, entries);
		} catch (Exception e) {
			throw new ExertionException(e);
		}
	}

	public static <T extends Exertion> T exert(T input,
			Transaction transaction, Parameter... entries)
			throws ExertionException {
        return ExertionExecutor.exert(input, transaction, entries);
	}

	public static OutEntry output(Object value) {
		return new OutEntry(null, value, 0);
	}

	public static ReturnPath self() {
		return new ReturnPath();
	}

	public static ReturnPath result(String path, String... paths) {
		return new ReturnPath(path, paths);
	}

	public static ReturnPath result(String path, Direction direction,
			String... paths) {
		return new ReturnPath(path, direction, paths);
	}

	public static ReturnPath result(String path, Class type, String... paths) {
		return new ReturnPath(path, Direction.OUT, type, paths);
	}

	public static OutEntry output(String path, Object value) {
		return new OutEntry(path, value, 0);
	}

	public static OutEntry out(String path, Object value) {
		return new OutEntry(path, value, 0);
	}

	public static OutEntry entry(String path, IndexedTriplet fidelity) {
		return new OutEntry(path, fidelity);
	}

	public static OutEntry out(String path, IndexedTriplet fidelity) {
		return new OutEntry(path, fidelity);
	}

	public static OutEndPoint output(Exertion outExertion, String outPath) {
		return new OutEndPoint(outExertion, outPath);
	}

	public static OutEndPoint out(Exertion outExertion, String outPath) {
		return new OutEndPoint(outExertion, outPath);
	}

	public static InEndPoint input(Exertion inExertion, String inPath) {
		return new InEndPoint(inExertion, inPath);
	}

	public static InEndPoint in(Exertion inExertion, String inPath) {
		return new InEndPoint(inExertion, inPath);
	}

	public static OutEntry output(String path, Object value, int index) {
		return new OutEntry(path, value, index);
	}

	public static OutEntry out(String path, Object value, int index) {
		return new OutEntry(path, value, index);
	}

	public static OutEntry output(String path, Object value, boolean flag) {
		return new OutEntry(path, value, flag);
	}

	public static OutEntry out(String path, Object value, boolean flag) {
		return new OutEntry(path, value, flag);
	}

	public static InEntry input(String path) {
		return new InEntry(path, null, 0);
	}

	public static OutEntry out(String path) {
		return new OutEntry(path, null, 0);
	}

	public static OutEntry output(String path) {
		return new OutEntry(path, null, 0);
	}

	public static InEntry in(String path) {
		return new InEntry(path, null, 0);
	}

	public static Entry at(String path, Object value) {
		return new Entry(path, value, 0);
	}

	public static Entry at(String path, Object value, int index) {
		return new Entry(path, value, index);
	}

	public static InEntry input(String path, Object value) {
		return new InEntry(path, value, 0);
	}

	public static InEntry in(String path, Object value) {
		return new InEntry(path, value, 0);
	}

	public static InEntry input(String path, Object value, int index) {
		return new InEntry(path, value, index);
	}

	public static InEntry in(String path, Object value, int index) {
		return new InEntry(path, value, index);
	}

	public static InEntry inout(String path) {
		return new InEntry(path, null, 0);
	}

	public static InEntry inout(String path, Object value) {
		return new InEntry(path, value, 0);
	}

	public static InoutEntry inout(String path, Object value, int index) {
		return new InoutEntry(path, value, index);
	}

    public static URL deleteURL(URL url) throws ExertionException,
			SignatureException, ContextException {
		String serviceTypeName = SdbUtil.getServiceType(url);
		String storageName = SdbUtil.getProviderName(url);
		Task objectStoreTask;
		try {
			objectStoreTask = task(
					"delete",
					sig("contextDelete", Class.forName(serviceTypeName),
							storageName),
					ContextFactory.context("delete",
                            in(StorageManagement.object_deleted, url),
                            result(StorageManagement.object_url)));
		} catch (ClassNotFoundException e) {
			throw new SignatureException("No such service type: "
					+ serviceTypeName, e);
		}
		return (URL) value(objectStoreTask);
	}

    public static URL deleteObject(Object object) throws ExertionException,
            SignatureException, ContextException {
        String storageName = SorcerEnv.getActualName(SorcerEnv
                .getDatabaseStorerName());
        Task objectStoreTask = task(
                "delete",
                sig("contextDelete", DatabaseStorer.class, storageName),
                ContextFactory.context("delete", in(StorageManagement.object_deleted, object),
                        result(StorageManagement.object_url)));
        return (URL) value(objectStoreTask);
    }

    @SuppressWarnings("unchecked")
    static public List<String> list(URL url, Store storeType)
            throws ExertionException, SignatureException, ContextException {
        Store type = storeType;
        String providerName = SdbUtil.getProviderName(url);
        if (providerName == null)
            providerName = SorcerEnv.getActualDatabaseStorerName();

        if (type == null) {
            type = SdbUtil.getStoreType(url);
            if (type == null) {
                type = Store.object;
            }
        }
        Task listTask = task("list",
                sig("contextList", DatabaseStorer.class, providerName),
                SdbUtil.getListContext(type));

        return (List<String>) Evaluator.value(listTask);
    }

    private static class Pipe {
		String inPath;
		String outPath;
		Exertion in;
		Exertion out;

		Pipe(OutEndPoint outEndPoint, InEndPoint inEndPoint) {
			this.out = outEndPoint.out;
			this.outPath = outEndPoint.outPath;
			this.in = inEndPoint.in;
			this.inPath = inEndPoint.inPath;
		}
	}

	public static Pipe pipe(OutEndPoint outEndPoint, InEndPoint inEndPoint) {
		return new Pipe(outEndPoint, inEndPoint);
	}

	public static <T> ControlContext strategy(T... entries) {
		ControlContext cc = new ControlContext();
		List<Signature> sl = new ArrayList<Signature>();
		for (Object o : entries) {
			if (o instanceof Access) {
				cc.setAccessType((Access) o);
			} else if (o instanceof Flow) {
				cc.setFlowType((Flow) o);
			} else if (o instanceof Monitor) {
				cc.isMonitorable((Monitor) o);
			} else if (o instanceof Provision) {
				cc.isProvisionable((Provision) o);
			} else if (o instanceof Wait) {
				cc.isWait((Wait) o);
			} else if (o instanceof Signature) {
				sl.add((Signature) o);
			}
		}
		cc.setSignatures(sl);
		return cc;
	}

	public static URL dbURL() throws MalformedURLException {
		return new URL(SorcerEnv.getDatabaseStorerUrl());
	}

	public static URL dsURL() throws MalformedURLException {
		return new URL(SorcerEnv.getDataspaceStorerUrl());
	}

	public static URL dbURL(Object object) throws ExertionException,
			SignatureException, ContextException {
		return store(object);
	}

	public static SosURL sosURL(Object object) throws ExertionException,
			SignatureException, ContextException {
        return new SosURL(store(object));
    }

	public static URL store(Object object) throws ExertionException,
			SignatureException, ContextException {
        String storageName = SorcerEnv.getActualName(SorcerEnv
                .getDatabaseStorerName());
        Task objectStoreTask = task(
                "store",
                sig("contextStore", DatabaseStorer.class, storageName),
                context("store", in(StorageManagement.object_stored, object),
                        result("result, stored/object/url")));
        return value(objectStoreTask);
    }

	public static Object retrieve(URL url) throws IOException {
		return url.getContent();
	}

	public static URL update(Object object) throws ExertionException,
			SignatureException, ContextException {
        if (!(object instanceof Identifiable)
                || !(((Identifiable) object).getId() instanceof Uuid)) {
            throw new ContextException("Object is not Uuid Identifiable: "
                    + object);
        }
        return update((Uuid) ((Identifiable) object).getId(), object);
    }

    static public URL update(Uuid storeUuid, Object value)
            throws ExertionException, SignatureException, ContextException {
        Task objectUpdateTask = task(
                "update",
                sig("contextUpdate", DatabaseStorer.class,
                        SorcerEnv.getActualDatabaseStorerName()),
                SdbUtil.getUpdateContext(value, storeUuid));

        objectUpdateTask = exert(objectUpdateTask);
        return (URL) get(ContextFactory.context(objectUpdateTask),
                StorageManagement.object_url);
    }

    public static List<String> list(URL url) throws ExertionException,
			SignatureException, ContextException {
        return list(url, null);

    }

    @SuppressWarnings("unchecked")
    static public List<String> list(Store storeType) throws ExertionException,
            SignatureException, ContextException {
        String storageName = SorcerEnv.getActualName(SorcerEnv
                .getDatabaseStorerName());

        Task listTask = task("contextList",
                sig("contextList", DatabaseStorer.class, storageName),
                SdbUtil.getListContext(storeType));

        return (List<String>) value(listTask);
    }

    public static URL delete(Object object) throws ExertionException,
			SignatureException, ContextException {
        if (object instanceof URL) {
            return deleteURL((URL) object);
        } else {
            return deleteObject(object);
        }
    }

	public static int clear(Store type) throws ExertionException,
			SignatureException, ContextException {
        String storageName = SorcerEnv.getActualName(SorcerEnv
                .getDatabaseStorerName());
        Task objectStoreTask = task(
                "clear",
                sig("contextClear", DatabaseStorer.class, storageName),
                context("clear", in(StorageManagement.store_type, type),
                        result(StorageManagement.store_size)));
        return (Integer) value(objectStoreTask);
    }

	public static int size(Store type) throws ExertionException,
			SignatureException, ContextException {
        String storageName = SorcerEnv.getActualName(SorcerEnv
                .getDatabaseStorerName());
        Task objectStoreTask = task(
                "size",
                sig("contextSize", DatabaseStorer.class, storageName),
                context("size", in(StorageManagement.store_type, type),
                        result(StorageManagement.store_size)));
        return (Integer) value(objectStoreTask);
    }

	private static class InEndPoint {
		String inPath;
		Exertion in;

		InEndPoint(Exertion in, String inPath) {
			this.inPath = inPath;
			this.in = in;
		}
	}

	private static class OutEndPoint {
		String outPath;
		Exertion out;

		OutEndPoint(Exertion out, String outPath) {
			this.outPath = outPath;
			this.out = out;
		}
	}

	public static Object target(Object object) {
		return new target(object);
	}

	public static ParameterTypes parameterTypes(Class... parameterTypes) {
		return new ParameterTypes(parameterTypes);
	}

	public static Args args(Object... args) {
		return new Args(args);
	}

	public static List<Service> providers(Signature signature)
			throws SignatureException {
		ServiceTemplate st = new ServiceTemplate(null,
				new Class[] { signature.getServiceType() }, null);
		ServiceItem[] sis = Accessor.getServiceItems(st, null);
		if (sis == null)
			throw new SignatureException("No available providers of type: "
					+ signature.getServiceType().getName());
		List<Service> servicers = new ArrayList<Service>(sis.length);
		for (ServiceItem si : sis) {
			servicers.add((Service) si.service);
		}
		return servicers;
	}

	public static List<Class<?>> interfaces(Object obj) {
		if (obj == null)
			return null;
		return Arrays.asList(obj.getClass().getInterfaces());
	}

	public static Object provider(Signature signature)
			throws SignatureException {
		Object target = null;
		Service provider = null;
		Class<?> providerType = null;
		if (signature instanceof NetSignature) {
			providerType = ((NetSignature) signature).getServiceType();
		} else if (signature instanceof ObjectSignature) {
			providerType = ((ObjectSignature) signature).getProviderType();
			target = ((ObjectSignature) signature).getTarget();
		}
		try {
			if (signature instanceof NetSignature) {
				provider = ((NetSignature) signature).getServicer();
				if (provider == null) {
					provider = (Service) Accessor.getService(signature);
					((NetSignature) signature).setServicer(provider);
				}
			} else if (signature instanceof ObjectSignature) {
				if (target != null) {
					return target;
				} else if (Provider.class.isAssignableFrom(providerType)) {
					return providerType.newInstance();
				} else {
					return instance((ObjectSignature) signature);
				}
			} else if (signature instanceof EvaluationSignature) {
				return ((EvaluationSignature) signature).getEvaluator();
			}
		} catch (Exception e) {
			throw new SignatureException("No signature provider avaialable", e);
		}
		return provider;
	}

	/**
	 * Returns an instance by constructor method initialization or by
	 * instance/class method initialization.
	 * 
	 * @param signature
	 * @return object created
	 * @throws SignatureException
	 */
	public static Object instance(ObjectSignature signature)
			throws SignatureException {
		if (signature.getSelector() == null
				|| signature.getSelector().equals("new"))
			return signature.newInstance();
		else
			return signature.initInstance();
	}

	/**
	 * Returns an instance by class method initialization with a service
	 * dataContext.
	 * 
	 * @param signature
	 * @return object created
	 * @throws SignatureException
	 */
	public static Object instance(ObjectSignature signature, Context context)
			throws SignatureException {
		return signature.build(context);
	}

}