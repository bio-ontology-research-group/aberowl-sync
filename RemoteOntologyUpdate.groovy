/**
 * The RemoteOntologyUpdate trolls through the existing ontology database and updates those which need updating.
 */
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
@Grab(group='redis.clients', module='jedis', version='2.6.2')

import groovyx.net.http.HTTPBuilder
import java.text.SimpleDateFormat
import db.*

String BIO_API_ROOT = 'http://data.bioontology.org/'
String BIO_API_KEY = '24e0413e-54e0-11e0-9d7b-005056aa3316'

def oBase = new OntologyDaatabase()
def allOnts = oBase.allOntologies()

allOnts.each { oRec ->
  if(oRec.source == 'manual') {
    return;
  } else if(oRec.source == 'bioportal') {
    try {
      new HTTPBuilder().get( uri: BIO_API_ROUTE + 'ontologies/' + oRec.id + '/submissions', query: [ 'apikey': BIO_API_KEY ] ) { eResp, submissions ->
        println '[' + resp.status + '] ' + ont.links.submissions
        if(!submissions[0]) {
          return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
        def lastSubDate = dateFormat.parse(submissions[0].released).toTimestamp().getTime() / 1000;
        
        if(lastSubDate > exOnt.lastSubDate) {
          exOnt.addNewSubmission([
            'released': lastSubDate,
            'download': ont.links.download
          ]) 

          println '[' + ont.acronym + '] Adding new version'
        } else {
          println '[' + ont.acronym + '] Nothing new to report'
        }

        oBase.saveOntology(exOnt)
      }
    } catch(groovyx.net.http.HttpResponseException e) {
      println "Ontology disappeared"
    }
  } else { // try it as a url
    
  }
}
