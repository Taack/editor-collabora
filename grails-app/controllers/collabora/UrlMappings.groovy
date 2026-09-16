package collabora

class UrlMappings {
    // https://sdk.collaboraonline.com/docs/How_to_integrate.html
    static mappings = {

        // We choose assets because no filter are used on those URL
        post "/noFilter/files/$id/contents"(controller: "wopiCollabora", action: "putFile")
        get "/noFilter/files/$id/contents"(controller: "wopiCollabora", action: "getFile")
        get "/noFilter/files/$id"(controller: "wopiCollabora", action: "checkFileInfo")
    }
}
