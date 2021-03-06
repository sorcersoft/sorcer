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

package org.sorcersoft.sorcer.resolver;

import org.rioproject.resolver.Resolver;
import org.rioproject.resolver.ResolverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sorcer.boot.util.ReferenceHolder;

import javax.inject.Inject;

import static sorcer.resolver.SorcerResolver.getResolver;

/**
 * @author Rafał Krupiński
 */
public class RioResolverActivator {
    private static final Logger log = LoggerFactory.getLogger(RioResolverActivator.class);

    @Inject
    public RioResolverActivator(ReferenceHolder<Resolver> resolverHolder) throws ResolverException {
        Resolver resolver = resolverHolder.get();
        if (resolver != null)
            throw new IllegalStateException("Global Resolver instance already set");
        resolver = getResolver();
        log.info("Setting global Resolver instance to {}", resolver);
        resolverHolder.set(resolver);
    }
}
