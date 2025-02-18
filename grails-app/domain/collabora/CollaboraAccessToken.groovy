package collabora

import crew.User
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class CollaboraAccessToken {

    Date dateCreated
    Long ttl
    User baseUser
    String accessToken

    static constraints = {
        accessToken unique: true
        ttl nullable: true
    }
}
