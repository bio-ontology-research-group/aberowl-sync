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
String ABEROWL_API = 'http://aber-owl.net/service/api/'

def oBase = new OntologyDatabase()
def newO = []

// Get Bioportal ontologies
println 'getting the list'
new HTTPBuilder(BIO_API_ROOT).get(path: 'ontologies', query: [ 'apikey': BIO_API_KEY ]) { resp, ontologies ->
println 'got the list'
  ontologies.each { ont ->
    def exOnt = oBase.getOntology(ont.acronym)
    if(!exOnt && ont.name.toLowerCase().indexOf("poker")==-1 && ont.acronym.toLowerCase().indexOf("poker")==-1) {
      println "Creating " + ont.acronym
      exOnt = oBase.createOntology([
        'id': ont.acronym,
        'name': ont.name,
        'source': 'bioportal'
      ])

      try {
        new HTTPBuilder().get(uri: BIO_API_ROOT+'ontologies/'+exOnt.id+'/submissions', query: [ 'apikey': BIO_API_KEY ]) { eResp, submissions ->
          if(submissions[0]) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm")
            def lastSubDate = dateFormat.parse(submissions[0].released).toTimestamp().getTime() / 1000 // /

            exOnt.addNewSubmission([
              'released': lastSubDate,
              'download': submissions[0].ontology.links.download?.trim()
            ])

            if(submissions[0].description) {
              exOnt.description = submissions[0].description
            }
            if(submissions[0].homepage) {
              exOnt.homepage = submissions[0].homepage
            }
            if(submissions[0].contact) {
              exOnt.contact = []
              submissions[0].contact.each { exOnt.contact << it.email }
            }

            newO.add(exOnt.id)
            oBase.saveOntology(exOnt)

            new HTTPBuilder().get( uri: ABEROWL_API + 'reloadOntology.groovy', query: [ 'name': exOnt.id ] ) { r, s ->
              println "Updated " + exOnt.id
              println s
            }
          }
        }
      } catch(groovyx.net.http.HttpResponseException e) {
        println "Ontology disappeared"
        println e.getMessage()
      } catch(java.net.SocketException e) {
        println "idk"
      }
    } else {
      println exOnt.id + " already exists in AberOWL"
    }
  }
}
