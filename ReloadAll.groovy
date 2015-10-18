/**
 * The RemoteOntologyUpdate trolls through the existing ontology database and updates those which need updating.
 */
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
@Grab(group='redis.clients', module='jedis', version='2.6.2')

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import static groovyx.net.http.Method.HEAD
import static groovyx.net.http.ContentType.TEXT
import java.text.SimpleDateFormat
import db.*

String BIO_API_ROOT = 'http://data.bioontology.org/'
String BIO_API_KEY = '24e0413e-54e0-11e0-9d7b-005056aa3316'
String ABEROWL_API = 'http://aber-owl.net/service/api/'

def oBase = new OntologyDatabase()
def allOnts = oBase.allOntologies()

allOnts.each { oRec ->
  new HTTPBuilder().get( uri: ABEROWL_API + 'reloadOntology.groovy', query: [ 'name': oRec.id ] ) { r, s ->
    println "Updated " + oRec.id
  }
}

