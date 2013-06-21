/*
 * Copyright to the original author or authors.
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

/*
 * This configuration is used to start a ProvisionMonitor, including an embedded Webster and
 * a Lookup Service
 */

import org.rioproject.config.Component

import sorcer.provider.boot.RioServiceDescriptorUtil;
import com.sun.jini.start.ServiceDescriptor
import org.rioproject.resolver.maven2.Repository

@Component('org.rioproject.start')
class StartMonitorConfig {

    String[] getMonitorConfigArgs(String rioHome) {
        def configArgs = [rioHome+'/configs/rio/common.groovy', rioHome+'/configs/rio/monitor.groovy']
        return configArgs as String[]
    }

    String[] getLookupConfigArgs(String rioHome) {
        def configArgs = [rioHome+'/configs/rio/common.groovy', rioHome+'/configs/rio/reggie.groovy']
        return configArgs as String[]
    }

    ServiceDescriptor[] getServiceDescriptors() {
        RioServiceDescriptorUtil.checkForLoopback()
        String m2Repo = Repository.getLocalRepository().absolutePath
        String rioHome = System.getProperty('RIO_HOME')

        def websterRoots = [rioHome+'/lib/rio/lib-dl', ';',
                            rioHome+'/lib/rio/lib',    ';',
                            rioHome+'/deploy', ';',
                            m2Repo]

        String policyFile = rioHome+'/configs/rio/rio.policy'

        def serviceDescriptors = [
            RioServiceDescriptorUtil.getWebster(policyFile, '0', websterRoots as String[]),
            RioServiceDescriptorUtil.getLookup(policyFile, getLookupConfigArgs(rioHome)),
            RioServiceDescriptorUtil.getMonitor(policyFile, getMonitorConfigArgs(rioHome))
        ]

        return (ServiceDescriptor[])serviceDescriptors
    }

}
