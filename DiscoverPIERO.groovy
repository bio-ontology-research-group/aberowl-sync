/**
 * The RemoteOntologyDiscover trolls through various sources to find new ontologies
 */
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
@Grab(group='redis.clients', module='jedis', version='2.6.2')

import groovyx.net.http.HTTPBuilder
import java.text.SimpleDateFormat
import db.*

String BIO_API_ROOT = 'http://data.bioontology.org/'
String BIO_API_KEY = '24e0413e-54e0-11e0-9d7b-005056aa3316'
String ABEROWL_API = 'http://localhost/api/'

def oBase = new OntologyDatabase()
def newO = []

def ont = "PIERO"
def exOnt = oBase.getOntology("PIERO")
if(!exOnt) {
  exOnt = oBase.createOntology([
				 'id': "PIERO"
				'name': 
			       ])
  
  try {
    new HTTPBuilder().get(uri: BIO_API_ROOT+'ontologies/'+exOnt.id+'/submissions', query: [ 'apikey': BIO_API_KEY ]) { eResp, submissions ->
      if(submissions[0]) {
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm")
	def lastSubDate = dateFormat.parse(submissions[0].released).toTimestamp().getTime() / 1000
	
	exOnt.addNewSubmission([
				 'released': lastSubDate,
				'download': submissions[0].ontology.links.download
			       ])
	
	newO.add(exOnt.id)
	oBase.saveOntology(exOnt)
      }
    }
  } catch(groovyx.net.http.HttpResponseException e) {
    println "Ontology disappeared"
  } catch(java.net.SocketException e) {
    println "idk"
  }

  newO.each { id ->
    new HTTPBuilder().get( uri: ABEROWL_API + 'reloadOntology.groovy', query: [ 'name': id ] ) { r, s ->
      println "Updated " + id
    }
  }
}
