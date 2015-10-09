/**
 * The RemoteOntologyDiscover trolls through various sources to find new ontologies
 */
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
@Grab(group='redis.clients', module='jedis', version='2.6.2')

import groovyx.net.http.HTTPBuilder
import java.text.SimpleDateFormat
import db.*
import groovy.json.*

String ABEROWL_API = 'http://aber-owl.net/service/api/'

def slurper = new JsonSlurper()
def obo = slurper.parse(new URL("http://www.obofoundry.org/registry/ontologies.jsonld"))

def newO = []
def oBase = new OntologyDatabase()

obo.ontologies.each { ontology ->
  def id = ontology.id?.toUpperCase()
  def title = ontology.title
  def description = ontology.description?:""
  def purl = ontology.ontology_purl
  def exOnt = oBase.getOntology(id)
  if(!exOnt && purl) { // if new, and has download link
    println "Creating " + id
    try {
      exOnt = oBase.createOntology([
				     'id': id,
				    'name': title,
				    'description': description,
				    'source': 'obofoundry',
				   ])
      exOnt.addNewSubmission([
			       'released': (int) (System.currentTimeMillis() / 1000L), // current unix time (pretty disgusting line though)
			      'download': purl
			     ])

      oBase.saveOntology(exOnt)
      newO.add(exOnt.id)
    } catch (Exception E) {
      println "Failure to download $id"
    }
  }
}

newO.each { id ->
  new HTTPBuilder().get( uri: ABEROWL_API + 'reloadOntology.groovy', query: [ 'name': id ] ) { r, s ->
    println "Updated " + id
  }
}
