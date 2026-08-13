(function() {
    Object.defineProperty(window, "prefpaneSetupserviceJSLoaded", {
        value: true,
        writable: false,
        configurable: true,
    });
    window.InternetAccount = {agreedToTerms: false, transferCookie: function() { }};
    methods = [
        "log",
        "cancel",
        "resizeToWindow",
        "confirmWithCallback", //(provided in prefpange-setupservice.js)
        "updateSucceeded",
        "register",
    ];
    for (let method of methods) {
        window.InternetAccount[method] = function() {
            if (method == "resizeToWindow") {
                document.querySelector('meta[name="viewport"]').content = `width=${arguments[0]}`;
            }
            if (method != "log") {
                console.log("Called " + method + " with data", arguments);
            }
            window.flutter_inappwebview.callHandler(method, ...arguments);
        }
    }
})()