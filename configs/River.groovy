/**
 * Deployment configuration for the minimum Sorcer
 *
 * @author Pawel Rubach based on Dennis Reedy's example
 */
import sorcer.core.SorcerEnv;

def getSorcerHome() {
    return SorcerEnv.homeDir.path;
}

def getSorcerVersion() {
    return sorcerVersion = SorcerEnv.getSorcerVersion();
}

deployment(name: 'Sorcer') {
    groups SorcerEnv.getLookupGroups() as String[];

    codebase SorcerEnv.websterUrl;

    artifact id: 'webster-srv', 'org.sorcersoft.sorcer:sos-webster:' + getSorcerVersion()

    artifact id: 'reggie', 'org.apache.river:reggie:2.2.2'
    artifact id: 'reggie-dl', 'org.apache.river:reggie-dl:2.2.2'

    service(name: 'Webster') {
        implementation(class: 'sorcer.tools.webster.Webster') {
            artifact ref: 'webster-srv'
        }
        configuration file: getSorcerHome() + "/configs/webster/configs/webster-prv.config"
        maintain 1
    }

    service(name: 'Reggie') {
        interfaces {
            classes 'com.sun.jini.reggie.Registrar'
            artifact ref: 'reggie-dl'
        }
        implementation(class: 'com.sun.jini.reggie.TransientRegistrarImpl') {
            artifact ref: 'reggie'
        }
        configuration file: getSorcerHome() + "/configs/jini/configs/reggie.config"
        maintain 1
    }
}
