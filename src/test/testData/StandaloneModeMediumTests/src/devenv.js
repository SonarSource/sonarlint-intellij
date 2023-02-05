qq(window).attach("load", function() {
    "use strict";

    s3Uploader = new qq.s3.FineUploader({
        element: document.getElementById("s3-example"),
        debug: true,
        request: {
            endpoint: "http://fineuploadertest.s3.amazonaws.com",
            accessKey: "AKIAIXVR6TANOGNBGANQ"
        },
        signature: {
            endpoint: "/test/dev/handlers/vendor/fineuploader/php-s3-server/endpoint.php"
        },
        uploadSuccess: {
            endpoint: "/test/dev/handlers/vendor/fineuploader/php-s3-server/endpoint.php?success"
        },
        iframeSupport: {
            localBlankPagePath: "success.html"
        },
        chunking: {
            enabled: true,
            concurrent: {
                enabled: true
            }
        },
        resume: {
            enabled: true
        },
        retry: {
            enableAuto: true,
            showButton: true
        },
        deleteFile: {
            enabled: true,
            endpoint: "/test/dev/handlers/vendor/fineuploader/php-s3-server/endpoint.php",
            forceConfirm: true,
            params: {
                foo: "bar"
            }
        },
        failedUploadTextDisplay: {
            mode: "custom"
        },
        display: {
            fileSizeOnSubmit: true
        },
        paste: {
            targetElement: document,
            promptForName: true
        },
        thumbnails: {
            placeholders: {
                waitingPath: "/client/placeholders/waiting-generic.png",
                notAvailablePath: "/client/placeholders/not_available-generic.png"
            }
        },
        workarounds: {
            ios8BrowserCrash: false
        },
        callbacks: {
            onError: errorHandler,
            onUpload: function(id, _filename) {
                this.setParams({
                    "hey": "hi ɛ $ hmm \\ hi",
                    "ho": "foobar"
                }, id);

            },
            onStatusChange: function(id, _oldS, newS) {
                qq.log("id: " + id + " " + newS);
            },
            onComplete: function(_id, _name, response) {
                qq.log(response);
            }
        }
    });
});
