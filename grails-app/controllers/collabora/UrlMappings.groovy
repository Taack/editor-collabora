package collabora

class UrlMappings {
    // https://sdk.collaboraonline.com/docs/How_to_integrate.html
    static mappings = {
        post "/wopi/files/$id/contents"(controller: "wopiCollabora", action: "putFile")
        get "/wopi/files/$id/contents"(controller: "wopiCollabora", action: "getFile")
        get "/wopi/files/$id"(controller: "wopiCollabora", action: "checkFileInfo")
    }
}
