package collabora

import attachment.Attachment
import crew.User
import grails.compiler.GrailsCompileStatic
import groovy.transform.CompileStatic
import taack.ast.annotation.TaackFieldEnum
import taack.ui.dsl.common.ActionIcon

@CompileStatic
enum CollaboraApp {
    WRITER(CollaboraIcons.WRITER),
    CALC(CollaboraIcons.CALC),
    IMPRESS(CollaboraIcons.IMPRESS),
    DRAW(CollaboraIcons.DRAW)

    CollaboraApp(ActionIcon actionIcon) {
        this.actionIcon = actionIcon
    }

    ActionIcon actionIcon
}

@GrailsCompileStatic
@TaackFieldEnum
class CollaboraTemplate {

    Date dateCreated
    User userCreated
    Date lastUpdated
    User userUpdated

    CollaboraApp collaboraApp
    String description
    Attachment attachment

    static constraints = {
    }
}
