(ns linkedin-profile-api.url-spec
  (:require [speclj.core :refer :all]
            [linkedin-profile-api.url :as url]))

(describe "linkedin-profile-api.url/valid-profile-url?"
  (it "accepts a standard linkedin profile url"
    (should (url/valid-profile-url? "https://www.linkedin.com/in/janedoe")))
  (it "accepts a linkedin profile url with trailing slash"
    (should (url/valid-profile-url? "https://www.linkedin.com/in/janedoe/")))
  (it "accepts a linkedin profile url without the www subdomain"
    (should (url/valid-profile-url? "https://linkedin.com/in/janedoe")))
  (it "accepts a public id with digits and underscores"
    (should (url/valid-profile-url? "https://www.linkedin.com/in/ACoAAB12345_abcdefghijklm")))
  (it "rejects a non-url string"
    (should-not (url/valid-profile-url? "not a url")))
  (it "rejects a url on a foreign domain"
    (should-not (url/valid-profile-url? "https://example.com/in/janedoe")))
  (it "rejects a company page"
    (should-not (url/valid-profile-url? "https://www.linkedin.com/company/acme")))
  (it "rejects a profile url with an empty public id"
    (should-not (url/valid-profile-url? "https://www.linkedin.com/in/")))
  (it "rejects an empty string"
    (should-not (url/valid-profile-url? "")))
  (it "rejects nil"
    (should-not (url/valid-profile-url? nil)))
  (it "rejects a url with a disallowed character in the public id"
    (should-not (url/valid-profile-url? "https://www.linkedin.com/in/jane doe")))
  (it "rejects a non-https url"
    (should-not (url/valid-profile-url? "http://www.linkedin.com/in/janedoe"))))

(describe "linkedin-profile-api.url/extract-public-id"
  (it "extracts the public id from a profile url"
    (should= "janedoe" (url/extract-public-id "https://www.linkedin.com/in/janedoe")))
  (it "extracts the public id ignoring a trailing slash"
    (should= "janedoe" (url/extract-public-id "https://www.linkedin.com/in/janedoe/")))
  (it "returns nil for an invalid profile url"
    (should= nil (url/extract-public-id "https://www.linkedin.com/company/acme"))))
