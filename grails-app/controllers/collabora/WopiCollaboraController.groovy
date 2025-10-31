package collabora


import attachment.Attachment
import crew.AttachmentController
import crew.User
import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured
import grails.web.api.WebAttributes
import org.codehaus.groovy.runtime.MethodClosure
import taack.domain.TaackAttachmentService
import taack.render.TaackSaveService
import taack.render.TaackUiService
import taack.ui.dsl.common.ActionIcon

import static taack.ui.TaackUi.createForm

@GrailsCompileStatic
@Secured(['permitAll'])
class WopiCollaboraController implements WebAttributes {

    CollaboraEditorService collaboraEditorService
    TaackUiService taackUiService
    TaackSaveService taackSaveService
    TaackAttachmentService taackAttachmentService

    def getFile() {
        Long id = params.long('id')
        String accessToken = params.get('access_token')
        User u = CollaboraAccessToken.findByAccessToken(accessToken)?.baseUser
        if (u) {
            Attachment a = Attachment.read(id)

            if (a && collaboraEditorService.hasWriteAccess(a, u)) {
                collaboraEditorService.getFile(u, a)
            } else {
                log.error("Attachment is null ($id) or user has no access")
                render(status: 401, text: 'nok')
            }
        } else {
            log.error("user is null ($accessToken")
            render 'NOK'
        }

    }

    @Transactional
    def putFile() {
        Long id = params.long('id')
        String accessToken = params.get('access_token')
        // ZonedDateTime lastModifiedTime = ZonedDateTime.parse(params['LastModifiedTime'].toString())
        User u = collaboraEditorService.getUserFromToken(accessToken)
        if (u) {
            Attachment a = Attachment.get(id)
            if (a) {
                if (request.post) {
                    collaboraEditorService.putFile(u, a, request.inputStream.readAllBytes())
                    render 'OK'
                } else {
                    log.error("Not POST")
                    render 'nok0'
                }
            } else {
                log.error("Attachment is null ($id")
                render 'nok'
            }
        } else {
            log.error("user is null ($accessToken")
            render 'NOK'
        }
    }

    def checkFileInfo() {
        Long id = params.long('id')
        String accessToken = params.get('access_token')

        User u = CollaboraAccessToken.findByAccessToken(accessToken)?.baseUser
        if (u) {
            Attachment a = Attachment.read(id)
            if (a) {
                boolean canWrite = collaboraEditorService.hasWriteAccess(a, u)
                Map info = [
                        BaseFileName          : a.originalName,
                        OwnerId               : a.userCreated.id,
                        Size                  : a.fileSize,
                        UserId                : u.id,
                        UserFriendlyName      : u.username,
                        UserCanWrite          : canWrite,
                        HidePrintOption       : true,
                        DisablePrint          : true,
                        HideSaveOption        : !canWrite,
                        HideExportOption      : false,
                        DisableExport         : false,
                        DisableCopy           : true,
                        EnableOwnerTermination: true,
                        SupportsRename        : false,
//                LastModifiedTime: a.,
                ]
                render(info as JSON)
            } else {
                log.error("Attachment is null ($id")
                render 'nok'
            }
        } else {
            log.error("user is null ($accessToken")
            render 'NOK'
        }
    }

    @Secured(['ROLE_ADMIN', 'ROLE_TEMPLATE_ADMIN'])
    def editTemplate(CollaboraTemplate collaboraTemplate) {
        collaboraTemplate ?= new CollaboraTemplate()
        taackUiService.createModal {
            form(createForm(collaboraTemplate, {
                field collaboraTemplate.collaboraApp_
                field collaboraTemplate.description_
                ajaxField collaboraTemplate.attachment_, AttachmentController.&selectAttachment as MethodClosure
                formAction this.&saveCollaboraTemplate as MethodClosure
            }))
        }
    }

    @Transactional
    @Secured(['ROLE_ADMIN', 'ROLE_TEMPLATE_ADMIN'])
    def saveCollaboraTemplate() {
        taackSaveService.saveThenReloadOrRenderErrors(CollaboraTemplate)
    }

    def createFromTemplate() {
        taackUiService.createModal {
            table collaboraEditorService.createCollaboraTemplateTable(true), {
                menuIcon ActionIcon.ADD, this.&editTemplate as MethodClosure
            }
        }
    }

    @Transactional
    def duplicateAttachmentAndEdit(CollaboraTemplate template) {
        Attachment newAttachment = taackAttachmentService.cloneToNewAttachment(template.attachment)
        newAttachment.save(flush: true, failOnError: true)
        if (newAttachment.hasErrors())
            log.error "${newAttachment.errors}"
        else
            redirect(controller: 'attachment', action: 'showAttachment', id: newAttachment.id)
    }
}
