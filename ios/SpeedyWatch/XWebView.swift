import SwiftUI
import WebKit

@MainActor
final class XWebController: NSObject, ObservableObject {
    static let homeURL = URL(string: "https://x.com/")!

    @Published private(set) var currentURL: URL?
    @Published private(set) var pageTitle = "X"
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    private weak var webView: WKWebView?
    private var urlObservation: NSKeyValueObservation?
    private var didStartLoading = false

    func attach(_ webView: WKWebView) {
        self.webView = webView
        urlObservation = webView.observe(\.url, options: [.initial, .new]) { [weak self] webView, _ in
            Task { @MainActor in
                self?.currentURL = webView.url
            }
        }
        // X is started lazily when the user selects it.
    }

    func loadHome() {
        if didStartLoading {
            load(Self.homeURL)
        } else {
            startIfNeeded()
        }
    }

    func reload() {
        webView?.reload()
    }

    func goBack() {
        if webView?.canGoBack == true {
            webView?.goBack()
        }
    }

    func goForward() {
        if webView?.canGoForward == true {
            webView?.goForward()
        }
    }

    func dismissError() {
        errorMessage = nil
    }

    func load(_ url: URL) {
        guard XURLPolicy.isSupportedSource(url) else {
            errorMessage = "Only HTTPS X links can be opened in SpeedyWatch"
            return
        }
        webView?.load(URLRequest(url: url))
    }

    private func startIfNeeded() {
        guard !didStartLoading else { return }
        didStartLoading = true
#if DEBUG
        let requestedURL = ProcessInfo.processInfo.environment["SPEEDYWATCH_TEST_X_URL"].flatMap(URL.init(string:))
        let initialURL = requestedURL.map(XURLPolicy.isSupportedSource) == true
            ? requestedURL! : Self.homeURL
#else
        let initialURL = Self.homeURL
#endif
        Task { @MainActor [weak self] in
            guard let self, let webView = self.webView else { return }
            if let ruleList = await XAdFilter.compileContentRuleList() {
                webView.configuration.userContentController.add(ruleList)
            }
            webView.load(URLRequest(url: initialURL))
        }
    }
}

extension XWebController: WKNavigationDelegate {
    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.cancel)
            return
        }
        if XURLPolicy.isAllowedNavigation(url) {
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
        pageTitle = webView.title?.isEmpty == false ? webView.title! : "X"
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        isLoading = false
        errorMessage = "X could not be loaded"
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        isLoading = false
        errorMessage = "X could not be loaded"
    }
}

extension XWebController: WKUIDelegate {
    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for navigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        if navigationAction.targetFrame == nil, let url = navigationAction.request.url {
            if XURLPolicy.isAllowedNavigation(url) {
                webView.load(navigationAction.request)
            } else {
                UIApplication.shared.open(url)
            }
        }
        return nil
    }
}

struct XWebView: UIViewRepresentable {
    @ObservedObject var controller: XWebController

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        configuration.websiteDataStore = .default()
        configuration.userContentController.addUserScript(WKUserScript(
            source: XAdFilter.injectedScript,
            injectionTime: .atDocumentEnd,
            forMainFrameOnly: true
        ))

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = controller
        webView.uiDelegate = controller
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.isOpaque = false
        webView.backgroundColor = UIColor(red: 15 / 255, green: 15 / 255, blue: 15 / 255, alpha: 1)
        webView.isInspectable = false
        controller.attach(webView)
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

struct XAdFilter {
    static let injectedScript = #"""
    (() => {
      if (window.__speedyWatchXAdFilterInstalled) return;
      window.__speedyWatchXAdFilterInstalled = true;

      const markerPattern = /^(ad|promoted|sponsored)$/i;
      const markerSelectors = [
        '[data-testid="placementTracking"]',
        '[data-testid="socialContext"]',
        '[aria-label*="Promoted" i]',
        '[aria-label*="Sponsored" i]'
      ];

      const postFor = (element) => element.closest('article, [role="article"]');
      const removePost = (element) => {
        const post = postFor(element);
        if (post) {
          post.remove();
          return;
        }
        const cell = element.closest('[data-testid="cellInnerDiv"]');
        (cell || element).remove();
      };

      const removeSponsoredPosts = () => {
        for (const selector of markerSelectors) {
          for (const marker of document.querySelectorAll(selector)) {
            if (selector.includes('placementTracking')) {
              removePost(marker);
              continue;
            }
            const label = (marker.getAttribute('aria-label') || marker.textContent || '').trim();
            if (markerPattern.test(label)) removePost(marker);
          }
        }

        for (const post of document.querySelectorAll('article, [role="article"]')) {
          const marker = [...post.querySelectorAll('[dir="auto"], span, div')]
            .find((element) => markerPattern.test((element.textContent || '').trim()));
          if (marker && post.querySelector('[data-testid="tweet"], [data-testid="User-Name"]')) {
            removePost(post);
          }
        }
      };

      let scheduled = false;
      const schedule = () => {
        if (scheduled) return;
        scheduled = true;
        requestAnimationFrame(() => {
          scheduled = false;
          removeSponsoredPosts();
        });
      };

      removeSponsoredPosts();
      new MutationObserver(schedule).observe(document.documentElement, {
        childList: true,
        subtree: true
      });
    })();
    """#

    static let contentRuleListJSON = #"""
    [
      {
        "trigger": {
          "url-filter": "^https://(ads-api\\.twitter\\.com|ads\\.twitter\\.com|ads-api\\.x\\.com)/"
        },
        "action": { "type": "block" }
      }
    ]
    """#

    @MainActor
    static func compileContentRuleList() async -> WKContentRuleList? {
        await withCheckedContinuation { continuation in
            WKContentRuleListStore.default().compileContentRuleList(
                forIdentifier: "SpeedyWatchXAds",
                encodedContentRuleList: contentRuleListJSON
            ) { ruleList, _ in
                continuation.resume(returning: ruleList)
            }
        }
    }
}