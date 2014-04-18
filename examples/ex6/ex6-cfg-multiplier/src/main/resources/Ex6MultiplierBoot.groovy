/**
 * Deployment configuration for ex6-prv
 *
 * @author Pawel Rubach
 */
import sorcer.core.SorcerEnv;

String[] getInitialMemberGroups() {
    def groups = SorcerEnv.getLookupGroups();
    return groups as String[]
}

def getSorcerVersion() {
    return sorcerVersion = SorcerEnv.getSorcerVersion();
}

def String getCodebase() {
    return SorcerEnv.getWebsterUrl();
}


deployment(name: 'ex6-multiplier') {
    groups getInitialMemberGroups();

    codebase getCodebase()

    artifact id: 'ex6-api', 'org.sorcersoft.sorcer:ex6-dl:pom:' + getSorcerVersion()
    artifact id:'ex6-cfg', 'org.sorcersoft.sorcer:ex6-cfg-multiplier:'+getSorcerVersion()

    service(name:'Multiplier') {
         interfaces {
             classes 'sorcer.arithmetic.provider.Multiplier'
             artifact ref:'ex6-api'
         }
         implementation(class: 'sorcer.core.provider.ServiceTasker') {
             artifact ref:'ex6-cfg'
         }
         configuration file: "classpath:multiplier-prv.config"
         maintain 1
     }
}
