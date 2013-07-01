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
package sorcer.service;

import net.jini.core.lookup.ServiceItem;
import sorcer.core.Provider;

/**
 * The creational factory interface used by the {@link Accessor}
 * facility.
 */
public interface DynamicAccessor {

	/**
	 * Returns a servicer matching its {@link Signature}.
	 * 
	 * @param signature
	 *            the signature of requested servicer
	 * @return the requested {@link Service}
	 * @throws SignatureException 
	 */
	Service getServicer(Signature signature) throws SignatureException;
	
	/**
	 * Returns a service item containing the servicer matching its {@link Signature}.
	 * 
	 * @param signature
	 *            the signature of requested servicer
	 * @return the requested {@link Service}
	 * @throws sorcer.service.SignatureException
	 */
	ServiceItem getServiceItem(Signature signature) throws SignatureException;

    Provider getProvider(String name, Class<?> type);
}