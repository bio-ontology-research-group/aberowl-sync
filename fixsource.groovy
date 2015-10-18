/**
 * set source to bioport
 */
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
@Grab(group='redis.clients', module='jedis', version='2.6.2')

import db.*
import groovy.json.*

def oBase = new OntologyDatabase()
def allOnts = oBase.allOntologies()
def updated = []
def updatedUrl = []

allOnts.each { oRec ->
  if(!oRec.source || oRec.source == '') {
    oRec.source = 'bioportal'
    println 'updated '  + oRec.id
    oBase.saveOntology(oRec)
  }
}
