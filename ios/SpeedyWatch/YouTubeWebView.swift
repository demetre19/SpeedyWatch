import SwiftUI
import WebKit

@MainActor
final class YouTubeWebController: NSObject, ObservableObject {
    static let homeURL = URL(string: "https://www.youtube.com/")!

    @Published private(set) var currentURL: URL?
    @Published private(set) var pageTitle = "YouTube"
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    private weak var webView: WKWebView?
    private var speed: Double = 1
    private var adSkipping = true
    private var urlObservation: NSKeyValueObservation?

    func attach(_ webView: WKWebView) {
        self.webView = webView
        urlObservation = webView.observe(\.url, options: [.initial, .new]) { [weak self] webView, _ in
            Task { @MainActor in
                self?.currentURL = webView.url
            }
        }
    }

    func loadHome() { webView?.load(URLRequest(url: Self.homeURL)) }
    func reload() { webView?.reload() }
    func goBack() { if webView?.canGoBack == true { webView?.goBack() } }
    func goForward() { if webView?.canGoForward == true { webView?.goForward() } }

    func dismissError() { errorMessage = nil }

    var liveURL: URL? { webView?.url ?? currentURL }

    func load(_ url: URL) {
        guard YouTubeURLPolicy.isSupportedSource(url) else {
            errorMessage = "Only HTTPS YouTube links can be opened in SpeedyWatch"
            return
        }
        webView?.load(URLRequest(url: url))
    }

    func setSpeed(_ value: Double) {
        speed = min(4, max(0.25, value))
        applyControllerState()
    }

    func setAdSkipping(_ enabled: Bool) {
        adSkipping = enabled
        applyControllerState()
    }

    func seek(to seconds: TimeInterval) {
        let bounded = min(604_800, max(0, seconds))
        let script = "window.__speedyWatchController ? window.__speedyWatchController.seekTo(\(bounded)) : false"
        webView?.evaluateJavaScript(script)
    }

    func captionMetadata() async throws -> [String: Any]? {
        guard let webView else { return nil }
        let script = "window.__speedyWatchController ? window.__speedyWatchController.getCaptionTrack() : null"
        let result = try await webView.evaluateJavaScript(script)
        guard let json = result as? String, let data = json.data(using: .utf8) else { return nil }
        return try JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    func userAgent() async -> String {
        guard let webView else { return "" }
        return (try? await webView.evaluateJavaScript("navigator.userAgent")) as? String ?? ""
    }

    func cookies() async -> [HTTPCookie] {
        guard let store = webView?.configuration.websiteDataStore.httpCookieStore else { return [] }
        return await withCheckedContinuation { continuation in
            store.getAllCookies { continuation.resume(returning: $0) }
        }
    }

    private func applyControllerState() {
        let script = """
        (() => {
          const controller = window.__speedyWatchController;
          if (!controller) return 'missing';
          controller.setSpeed(\(speed));
          controller.setAdSkipping(\(adSkipping ? "true" : "false"));
          return controller.status();
        })()
        """
        webView?.evaluateJavaScript(script)
    }
}

extension YouTubeWebController: WKNavigationDelegate {
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.cancel)
            return
        }
        if YouTubeURLPolicy.isAllowedNavigation(url) {
            decisionHandler(.allow)
        } else {
            decisionHandler(.cancel)
            UIApplication.shared.open(url)
        }
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        isLoading = true
        errorMessage = nil
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        isLoading = false
        currentURL = webView.url
        pageTitle = webView.title?.replacingOccurrences(of: " - YouTube", with: "") ?? "YouTube"
        applyControllerState()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        isLoading = false
        errorMessage = "YouTube could not be loaded"
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        isLoading = false
        errorMessage = "YouTube could not be loaded"
    }
}

extension YouTubeWebController: WKUIDelegate {
    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for navigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        if navigationAction.targetFrame == nil, let url = navigationAction.request.url {
            if YouTubeURLPolicy.isAllowedNavigation(url) {
                webView.load(navigationAction.request)
            } else {
                UIApplication.shared.open(url)
            }
        }
        return nil
    }
}

struct YouTubeWebView: UIViewRepresentable {
    @ObservedObject var controller: YouTubeWebController

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        configuration.websiteDataStore = .default()

        if let scriptURL = Bundle.main.url(forResource: "speedywatch", withExtension: "js"),
           let script = try? String(contentsOf: scriptURL, encoding: .utf8) {
            configuration.userContentController.addUserScript(WKUserScript(
                source: script,
                injectionTime: .atDocumentEnd,
                forMainFrameOnly: true
            ))
        }

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = controller
        webView.uiDelegate = controller
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.isOpaque = false
        webView.backgroundColor = UIColor(red: 15 / 255, green: 15 / 255, blue: 15 / 255, alpha: 1)
        webView.isInspectable = false
        controller.attach(webView)
#if DEBUG
        let requestedURL = ProcessInfo.processInfo.environment["SPEEDYWATCH_TEST_URL"].flatMap(URL.init(string:))
        let initialURL = requestedURL.map(YouTubeURLPolicy.isSupportedSource) == true
            ? requestedURL! : YouTubeWebController.homeURL
#else
        let initialURL = YouTubeWebController.homeURL
#endif
        webView.load(URLRequest(url: initialURL))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
