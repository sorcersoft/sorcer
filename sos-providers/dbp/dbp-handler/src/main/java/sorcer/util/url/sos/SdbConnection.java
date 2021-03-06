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
package sorcer.util.url.sos;

import sorcer.co.tuple.InputEntry;
import sorcer.core.context.ServiceContext;
import sorcer.core.provider.DatabaseStorer;
import sorcer.core.provider.StorageManagement;
import sorcer.service.Accessor;
import sorcer.service.Context;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * @author Mike Sobolewski
 *
 * sdb URL = sos://serviceType/providerName#objectType=Uuid
 *
 * objectType = dataContext, exertion, table, var, varModel, object
 */
public class SdbConnection extends URLConnection {

	private StorageManagement store;

	private String serviceType;

	private String providerName;

	private DatabaseStorer.Store storeType;

	private String uuid;

	public  SdbConnection(URL url) {
		super(url);
		serviceType = getURL().getHost();
        String path = getURL().getPath();
        if (path != null && path.startsWith("/"))
            path = path.substring(1);
        if (path != null && path.isEmpty())
            path = null;
        providerName = path;
		String reference = getURL().getRef();
		int index = reference.indexOf('=');
		storeType = DatabaseStorer.Store.getStoreType(reference.substring(0, index));
		uuid = reference.substring(index + 1);
	}

	/* (non-Javadoc)
	 * @see java.net.URLConnection#connect()
	 */
	@Override
	public void connect() throws IOException {
        try {
            store = (StorageManagement) Accessor.getService(providerName, Class.forName(serviceType));
        } catch (ClassNotFoundException e) {
            throw new IOException("Could not access StorageManagement implementation " + serviceType, e);
        }
        connected = true;
	}

	@Override
	public Object getContent() throws IOException {
		Context outContext;
		if (!connected)
			connect();
        if (store == null)
            throw new IOException("Could not access StorageManagement implementation " + serviceType);
		try {
            outContext = null;
            if (store != null) {
                Context cxt = new ServiceContext();
                cxt.putInValue(StorageManagement.object_type, storeType);
                cxt.putInValue(StorageManagement.object_uuid, uuid);
                outContext = store.contextRetrieve(cxt);
            }
            return outContext.getValue(StorageManagement.object_retrieved);
		} catch (Exception e) {
            throw new IOException(e);
        }
	}

    private static <T>InputEntry<T> in(String path, T value) {
        return new InputEntry<T>(path, value, 0);
	}
}
