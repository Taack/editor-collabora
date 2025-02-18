package collabora

import attachement.AttachmentSecurityService
import attachement.AttachmentUiService
import attachment.Attachment
import crew.User
import grails.compiler.GrailsCompileStatic
import grails.gsp.PageRenderer
import grails.plugin.springsecurity.SpringSecurityService
import grails.util.Environment
import grails.web.api.WebAttributes
import groovy.xml.XmlParser
import org.codehaus.groovy.runtime.MethodClosure as MC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.taack.IAttachmentCreate
import org.taack.IAttachmentEditorIFrame
import taack.domain.TaackAttachmentService
import taack.domain.TaackFilterService
import taack.ui.dsl.UiMenuSpecifier
import taack.ui.dsl.UiTableSpecifier
import taack.ui.dsl.common.ActionIcon
import taack.ui.dsl.common.IconStyle

import javax.annotation.PostConstruct
import java.security.SecureRandom

import static taack.ui.TaackUi.createMenu
import static taack.ui.TaackUi.createTable

@GrailsCompileStatic
final class CollaboraEditorService implements IAttachmentCreate, IAttachmentEditorIFrame, WebAttributes {

    static lazyInit = false

    TaackAttachmentService taackAttachmentService
    AttachmentSecurityService attachmentSecurityService
    AttachmentUiService attachmentUiService
    SpringSecurityService springSecurityService
    TaackFilterService taackFilterService

    @Autowired
    PageRenderer g

    @Value('${collabora.url}')
    String collaboraUrl

    Map<String, String> collaboraView = null
    Map<String, String> collaboraEdit = null

    @PostConstruct
    void initTaackService() {
        if (collaboraUrl) {
            String collaboraDiscovery = collaboraUrl + '/hosting/discovery'
            log.info "collaboraDiscovery: $collaboraDiscovery"
            try {
                def wopiDiscovery = new XmlParser(false, false).parse(collaboraDiscovery)
                collaboraView = [:]
                collaboraEdit = [:]
                wopiDiscovery['*']['app']['*'].each {

                    Node n = it as Node
                    Map mAtt = n.attributes()

                    if (mAtt['name'] == 'edit') {
                        collaboraEdit[mAtt['ext'] as String] = mAtt['urlsrc'] as String
                    } else if (mAtt['name'] == 'view') {
                        collaboraView[mAtt['ext'] as String] = mAtt['urlsrc'] as String
                    }
                }
                log.info "collaboraEdit: $collaboraEdit"
                log.info "collaboraView: $collaboraView"
            } catch (e) {
                log.error("${e.message}")
            }

            TaackAttachmentService.registerEdit(this)
            TaackAttachmentService.registerCreate(this)
        }
    }

    private static final SecureRandom secureRandom = new SecureRandom() //threadsafe
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder() //threadsafe

    private static String generateNewToken() {
        byte[] randomBytes = new byte[64]
        secureRandom.nextBytes(randomBytes)
        return base64Encoder.encodeToString(randomBytes)
    }

    User getUserFromToken(String accessToken) {
        CollaboraAccessToken.findByAccessToken(accessToken)?.baseUser
    }

    void putFile(User userUpdated, Attachment attachment, byte[] newContent) {

        println "user $userUpdated, att $attachment ,attu ${attachment.userCreated}"

        attachmentSecurityService.canDownloadFile(attachment, userUpdated)
        Attachment a = taackAttachmentService.updateContentSameContentType(attachment, newContent)
        a.lastUpdated = new Date()
        a.userCreated = userUpdated
        a.userUpdated = userUpdated
        a.save()
    }

    void getFile(User userReading, Attachment attachment) {
        attachmentSecurityService.canDownloadFile(attachment, userReading)
        taackAttachmentService.downloadAttachment(attachment)
    }

    @Override
    List<String> getEditIFrameManagedExtensions() {
        return collaboraEdit?.keySet()?.toList() ?: []
    }

    boolean hasWriteAccess(Attachment attachment, User u) {
        attachmentSecurityService.canDownloadFile(attachment, u)
    }

    @Override
    String createEditIFrame(Attachment attachment) {

        User u = springSecurityService.currentUser as User
        if (hasWriteAccess(attachment, u)) {
            CollaboraAccessToken t = CollaboraAccessToken.findByBaseUser(u)

            if (!t) {
                CollaboraAccessToken.withNewTransaction {
                    String at = generateNewToken()
                    t = new CollaboraAccessToken()
                    t.baseUser = u
                    t.accessToken = at
                    t.save(flush: true, failOnError: true)
                    if (t.errors) {
                        log.error("${t.errors}")
                    }
                }
            }

            String scheme = webRequest.request.scheme
            String serverName = webRequest.request.serverName
            int serverPort = webRequest.request.serverPort

            String client = collaboraEdit[attachment.extension]
            String wopiSrc
            if (Environment.current == Environment.PRODUCTION) {
                wopiSrc = URLEncoder.encode("https://intranet3.citel.fr/wopi/files/${attachment.id}", 'UTF-8')
            } else {
                wopiSrc = "$scheme://$serverName:$serverPort/wopi/files/${attachment.id}"
            }


            return """\
                <form id="collabora-form" action="${client + 'WOPISrc=' + wopiSrc}" enctype="multipart/form-data" method="POST" target="collabora" style='display:none;'>
                    <input name="access_token" value="${t.accessToken}" type="hidden"/>
                    <input type="submit" value="Load Collabora Online"/>
                </form>
                <iframe id="collabora" name="collabora" width="100%" height="800px" allow="fullscreen *"></iframe>
                <script>
                    function loadCollabora() {
                        var formElem = document.getElementById("collabora-form")
                        formElem.submit()
                    }
                    loadCollabora();
                </script>
                """
        }
        return null
    }

    @Override
    UiMenuSpecifier editorCreate() {
        createMenu {
            menu WopiCollaboraController.&createFromTemplate as MC
            menuIcon CollaboraIcons.WRITER, WopiCollaboraController.&createFromTemplate as MC, [collaboraApp: CollaboraApp.WRITER]
            menuIcon CollaboraIcons.DRAW, WopiCollaboraController.&createFromTemplate as MC, [collaboraApp: CollaboraApp.DRAW]
            menuIcon CollaboraIcons.CALC, WopiCollaboraController.&createFromTemplate as MC, [collaboraApp: CollaboraApp.CALC]
            menuIcon CollaboraIcons.IMPRESS, WopiCollaboraController.&createFromTemplate as MC, [collaboraApp: CollaboraApp.IMPRESS]
        }
    }

    UiTableSpecifier createCollaboraTemplateTable(boolean select = false) {
        CollaboraTemplate ct = new CollaboraTemplate()
        Attachment a = new Attachment()
        createTable {
            header {
                column {
                    label('Preview')
                }
                column {
                    sortableFieldHeader ct.dateCreated_
                    sortableFieldHeader ct.userCreated_
                    sortableFieldHeader ct.lastUpdated_
                    sortableFieldHeader ct.userUpdated_
                }
                column {
                    sortableFieldHeader ct.attachment_, a.originalName_
                    sortableFieldHeader ct.attachment_, a.fileSize_
                    sortableFieldHeader ct.attachment_, a.contentTypeEnum_
                    sortableFieldHeader ct.attachment_, a.contentTypeCategoryEnum_
                }
                sortableFieldHeader ct.collaboraApp_
                label ct.description_
            }
            iterate(taackFilterService.getBuilder(CollaboraTemplate).build()) { CollaboraTemplate cti ->
                rowColumn {
                    rowFieldRaw this.attachmentUiService.preview(cti.attachment.id)
                }
                rowColumn {
                    rowField cti.dateCreated_
                    rowField cti.userCreated_
                    rowField cti.lastUpdated_
                    rowField cti.userUpdated_
                }
                rowColumn {
                    if (select)
                        rowAction ActionIcon.SELECT * IconStyle.SCALE_DOWN, WopiCollaboraController.&duplicateAttachmentAndEdit as MC, cti.id
                    rowField cti.attachment.originalName_
                    rowField cti.attachment.fileSize_
                    rowField cti.attachment.contentTypeEnum_
                    rowField cti.attachment.contentTypeCategoryEnum_
                }
                rowFieldRaw cti.collaboraApp.actionIcon.getHtml(cti.collaboraApp.toString())
                rowField cti.description_
            }
        }
    }
}
