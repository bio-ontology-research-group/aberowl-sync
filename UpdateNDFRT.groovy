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
String ABEROWL_API = 'http://localhost:8080/api/'

def oBase = new OntologyDatabase()
def allOnts = oBase.allOntologies()
def updated = []

oRec = oBase.getOntology("NDFRT")
if(oRec.source == 'manual') {
  return;
} else if(oRec.source == 'bioportal') {
  try {
    new HTTPBuilder().get( uri: BIO_API_ROOT + 'ontologies/' + oRec.id + '/submissions', query: [ 'apikey': BIO_API_KEY ] ) { eResp, submissions ->
      println '[' + eResp.status + '] ' + oRec.id
      if(!submissions[0]) {
	println "No releases"
	return;
      }
      
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
      def lastSubDate = dateFormat.parse(submissions[0].released).toTimestamp().getTime() / 1000;
      
      if(lastSubDate > oRec.lastSubDate) {
	oRec.addNewSubmission([
				'released': lastSubDate,
			       'download': submissions[0].ontology.links.download
			      ]) 
	
	updated.add(oRec.id)
	
	println '[' + oRec.id + '] Adding new version'
      } else {
	println '[' + oRec.id + '] Nothing new to report'
      }
      
      oBase.saveOntology(oRec)
    }
  } catch(groovyx.net.http.HttpResponseException e) {
    e.printStackTrace()
    println "Ontology disappeared: " + e.getMessage()
  } catch(java.net.SocketException e) {
    println "idk"
  }
} else if(oRec.source != null) { // try it as a url
  // We just attempt to add the new submission, since that will check if it is new or not
  oRec.addNewSubmission([
			  'released': (int) (System.currentTimeMillis() / 1000L), // current unix time (pretty disgusting line though)
			 'download': oRec.source
			]) 
}

updated.each { id ->
  new HTTPBuilder().get( uri: ABEROWL_API + 'reloadOntology.groovy', query: [ 'name': id ] ) { r, s ->
    println "Updated " + id
  }
}

