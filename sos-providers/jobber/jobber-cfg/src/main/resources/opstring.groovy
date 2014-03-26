/**
 * Deployment configuration for jobber-prv
 *
 * @author Pawel Rubach
 */

import sorcer.core.SorcerConstants
import sorcer.core.SorcerEnv;

String[] getInitialMemberGroups() {
    def groups = SorcerEnv.getLookupGroups();
    return groups as String[]
}

def String getCodebase() {
    return SorcerEnv.getWebsterUrl();
}

deployment(name: 'jobber-provider') {
    groups getInitialMemberGroups();

    codebase getCodebase()

    def jobber = [
            'impl': 'org.sorcersoft.sorcer:jobber-cfg:' + SorcerConstants.SORCER_VERSION,
            'dl'  : 'org.sorcersoft.sorcer:default-codebase:pom:' + SorcerConstants.SORCER_VERSION
    ]

    service(name: "Jobber") {
        interfaces {
            classes 'sorcer.core.provider.Jobber'
            artifact jobber.dl
        }
        implementation(class: 'sorcer.core.provider.jobber.ServiceJobber') {
            artifact jobber.impl
        }
        configuration file: 'classpath:jobber.config'
        maintain 1
    }
}
