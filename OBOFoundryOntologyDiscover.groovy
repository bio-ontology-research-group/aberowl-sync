/**
 * The RemoteOntologyDiscover trolls through various sources to find new ontologies
 */
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
@Grab(group='redis.clients', module='jedis', version='2.6.2')

import groovyx.net.http.HTTPBuilder
import java.text.SimpleDateFormat
import db.*
import groovy.json.*

List<String> ABEROWL_API = ['http://aber-owl.net/service/api/', 'http://aber-owl.net:55555/api/']

def slurper = new JsonSlurper()
def obo = slurper.parse(new URL("http://www.obofoundry.org/registry/ontologies.jsonld"))

def newO = []
def oBase = new OntologyDatabase()

obo.ontologies.each { ontology ->
  def id = ontology.id?.toUpperCase()
  def title = ontology.title
  def description = ontology.description?:""
  def purl = "http://purl.obolibrary.org/obo/"+id.toLowerCase()+".owl"
  def exOnt = oBase.getOntology(id)
  if(!exOnt && purl) { // if new, and has download link
    println "Creating " + id
    try {
      exOnt = oBase.createOntology([
				     'id': id,
				    'name': title,
				    'description': description,
				    'source': 'obofoundry'
				   ])
      println "Downloading from $purl..."
      exOnt.addNewSubmission([
			       'released': (int) (System.currentTimeMillis() / 1000L), // current unix time (pretty disgusting line though)
			      'download': purl
			     ])

      exOnt.purl = purl
      oBase.saveOntology(exOnt)
      ABEROWL_API.each {
	new HTTPBuilder().get( uri: it + 'reloadOntology.groovy', query: [ 'name': exOnt.id ] ) { r, s ->
	  println "Updated " + exOnt.id
	}
      }
      newO.add(exOnt.id)
    } catch (Exception E) {
      E.printStackTrace()
      println "Failure to download $id: $E"
    }
  } else if (exOnt && exOnt.source != 'obofoundry' && purl) {
    println exOnt.id + " not set to OBO Foundry source, but OBO purl available; updates source..."
    try {
      exOnt.source = 'obofoundry'
      if (description && description.length()>0) {
	exOnt.description = description
      }
      exOnt.purl = purl
      exOnt.addNewSubmission([
			       'released': (int) (System.currentTimeMillis() / 1000L), // current unix time (pretty disgusting line though)
			      'download': purl
			     ])
      ABEROWL_API.each {
	new HTTPBuilder().get( uri: it + 'reloadOntology.groovy', query: [ 'name': exOnt.id ] ) { r, s ->
	  println "Updated " + exOnt.id
	}
      }
      oBase.saveOntology(exOnt)
    } catch (Exception E) {
      println "Error loading "+exOnt.id+": "+E
    }
  } else if (exOnt.source == 'obofoundry' && exOnt.purl!=purl) { // purl changed (should not happen, only in first run)
    println exOnt.id + " purl changed, updating..."
    try {
      exOnt.source = 'obofoundry'
      if (description && description.length()>0) {
	exOnt.description = description
      }
      exOnt.purl = purl
      exOnt.addNewSubmission([
			       'released': (int) (System.currentTimeMillis() / 1000L), // current unix time (pretty disgusting line though)
			      'download': purl
			     ])
      ABEROWL_API.each {
	new HTTPBuilder().get( uri: it + 'reloadOntology.groovy', query: [ 'name': exOnt.id ] ) { r, s ->
	  println "Updated " + exOnt.id
	}
      }
      oBase.saveOntology(exOnt)
    } catch (Exception E) {
      println "Error loading "+exOnt.id+": "+E
    }
  }
}
