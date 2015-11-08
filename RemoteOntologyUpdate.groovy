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
import groovy.json.*

String BIO_API_ROOT = 'http://data.bioontology.org/'
String BIO_API_KEY = '24e0413e-54e0-11e0-9d7b-005056aa3316'
List<String> ABEROWL_API = ['http://aber-owl.net/service/api/', 'http://aber-owl.net:55555/api/']
//String ABEROWL_API = 'http://aber-owl.net/service/api/'
String OBOFOUNDRY_FILE = "http://www.obofoundry.org/registry/ontologies.jsonld"

def slurper = new JsonSlurper()
def obo = slurper.parse(new URL(OBOFOUNDRY_FILE))

def oBase = new OntologyDatabase()
def allOnts = oBase.allOntologies()
def updated = []
def updatedUrl = []

allOnts.each { oRec ->
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
        def lastSubDate = dateFormat.parse(submissions[0].released).toTimestamp().getTime() / 1000; // /

        if(lastSubDate > oRec.lastSubDate) {
          oRec.addNewSubmission([
            'released': lastSubDate,
		    'download': submissions[0].ontology.links.download?.trim()
          ]) 

          if(submissions[0].description) {
            oRec.description = submissions[0].description
          }
          if(submissions[0].homepage) {
            oRec.homepage = submissions[0].homepage
          }
          if(submissions[0].contact) {
            oRec.contact = []
            submissions[0].contact.each { oRec.contact << it.email }
          }

          try {
            ABEROWL_API.each {
              new HTTPBuilder().get( uri: it + 'reloadOntology.groovy', query: [ 'name': oRec.id ] ) { r, s ->
                println "Updated " + oRec.id
              }
            }
            oBase.saveOntology(oRec)
          } catch (Exception E) {
            println oRec.id+" failed update: "+E
          }

          println '[' + oRec.id + '] Added new version'
        } else if(!oRec.contact || !oRec.homepage) { // naughty patch codeZ
          if(submissions[0].homepage) {
            oRec.homepage = submissions[0].homepage
          }
          if(submissions[0].contact) {
            oRec.contact = []
            submissions[0].contact.each { oRec.contact << it.email }
          }
          oBase.saveOntology(oRec)
          println '[' + oRec.id + '] Added new metadata'
        } else {
          println '[' + oRec.id + '] Nothing new to report'
        }
      }
    } catch(groovyx.net.http.HttpResponseException e) {
      println "Ontology disappeared"
    } catch(java.net.SocketException e) {
      println "Socket exception"
    } catch(Exception e) {
      e.printStackTrace()
    }
  } else if(oRec.source == 'obofoundry') {
    obo.ontologies.findAll { it.id?.toLowerCase() == oRec.id?.toLowerCase() }.each { ont ->
      if (ont.description && ont.description.length()>0 && ont.description != oRec.description) {
	oRec.description = ont.description
	oBase.saveOntology(oRec)
      }
      if (ont.title && ont.title.length()>0 && ont.title != oRec.name) {
	oRec.name = ont.title
	oBase.saveOntology(oRec)
      }
      def purl = "http://purl.obolibrary.org/obo/"+ont.id?.toLowerCase()+".owl"
      try {
	oRec.addNewSubmission([
				'released': (int) (System.currentTimeMillis() / 1000L), // current unix time (pretty disgusting line though)
			       'download': purl
			      ]) 
	ABEROWL_API.each {
	  new HTTPBuilder().get( uri: it + 'reloadOntology.groovy', query: [ 'name': oRec.id ] ) { r, s ->
	    println "Updated " + oRec.id
	  }
	}
	oBase.saveOntology(oRec)
      } catch (Exception E) {}
    }
  } else if(oRec.source != null) { // try it as a url
    // We just attempt to add the new submission, since that will check if it is new or not
    try {
      oRec.addNewSubmission([
			      'released': (int) (System.currentTimeMillis() / 1000L), // current unix time (pretty disgusting line though) /
			     'download': oRec.source?.trim()
			    ]) 
      oBase.saveOntology(oRec)
    } catch (Exception E) {
      println "Failure do download "+oRec.id+" from "+oRec.source
    }
  }
}

/*
println "Updating ontologies obtained from BioPortal"
updated.each { id ->
  try {
    new HTTPBuilder().get( uri: ABEROWL_API + 'reloadOntology.groovy', query: [ 'name': id ] ) { r, s ->
      println "Updated " + id
    }
  } catch (Exception E) {
    println "$id failed update: "+E
  }
}
println "Updating ontologies obtained directly from URL"
updatedUrl.each { id ->
  println "Updating $id from URL..."
  try {
    new HTTPBuilder().get( uri: ABEROWL_API + 'reloadOntology.groovy', query: [ 'name': id ] ) { r, s ->
      println "Updated " + id
    }
  } catch (Exception E) {
    println "$id failed update: "+E
  }
}

*/
