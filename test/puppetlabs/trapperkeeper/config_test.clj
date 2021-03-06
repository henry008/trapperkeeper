(ns puppetlabs.trapperkeeper.config-test
  (:import (java.io StringReader FileNotFoundException))
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [bootstrap-services-with-cli-data with-app-with-cli-data]]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.services :refer [defservice]]))

(defprotocol ConfigTestService
  (test-fn [this ks])
  (test-fn2 [this])
  (get-in-config [this ks] [this ks default]))

(defservice test-service
            ConfigTestService
            [[:ConfigService get-in-config get-config]]
            (test-fn [this ks] (get-in-config ks))
            (test-fn2 [this] (get-config))
            (get-in-config [this ks] (get-in-config ks))
            (get-in-config [this ks default] (get-in-config ks default)))

(deftest test-config-service
  (testing "Fails if config path doesn't exist"
    (is (thrown-with-msg?
          FileNotFoundException
          #"Configuration path './foo/bar/baz' must exist and must be readable."
          (bootstrap-services-with-cli-data [test-service] {:config "./foo/bar/baz"}))))

  (testing "Can read values from a single .ini file"
    (with-app-with-cli-data app [test-service] {:config "./test-resources/config/file/config.ini"}
      (let [test-svc  (get-service app :ConfigTestService)]
      (is (= (test-fn test-svc [:foo :setting1]) "foo1"))
      (is (= (test-fn test-svc [:foo :setting2]) "foo2"))
      (is (= (test-fn test-svc [:bar :setting1]) "bar1"))

      (testing "`get-config` function"
        (is (= (test-fn2 test-svc) {:foo {:setting2 "foo2",
                                          :setting1 "foo1"},
                                    :bar {:setting1 "bar1"}
                                    :debug false} ))))))

  (testing "Can read values from a single .edn file"
    (with-app-with-cli-data app [test-service] {:config "./test-resources/config/file/config.edn"}
      (let [test-svc  (get-service app :ConfigTestService)]
        (testing "`get-config` function"
          (is (= {:debug false
                  :foo {:bar "barbar"
                        :baz "bazbaz"
                        :bam 42
                        :bap {:boozle "boozleboozle"
                              :bip [1 2 {:hi "there"} 3]}}}
                 (test-fn2 test-svc)))))))

  ;; NOTE: other individual file formats are tested in `typesafe-test`

  (testing "Can read values from a directory of .ini files"
    (with-app-with-cli-data app [test-service] {:config "./test-resources/config/inidir"}
      (let [test-svc  (get-service app :ConfigTestService)]
        (is (= (test-fn test-svc [:baz :setting1]) "baz1"))
        (is (= (test-fn test-svc [:baz :setting2]) "baz2"))
        (is (= (test-fn test-svc [:bam :setting1]) "bam1")))))

  (testing "A proper default value is returned if a key can't be found"
    (with-app-with-cli-data app [test-service] {:config "./test-resources/config/inidir"}
      (let [test-svc (get-service app :ConfigTestService)]
        (is (= (get-in-config test-svc [:doesnt :exist] "foo") "foo")))))

  (testing "Can read values from a directory of mixed config files"
    (with-app-with-cli-data app [test-service] {:config "./test-resources/config/mixeddir"}
      (let [test-svc (get-service app :ConfigTestService)
            cfg      (test-fn2 test-svc)]
        (is (= {:debug false
                :taco  {:burrito         [1, 2]
                        :nacho           "cheese"}
                :foo   {:bar             "barbar"
                        :baz             "bazbaz"
                        :meaningoflife   42}
                :baz   {:setting1        "baz1"
                        :setting2        "baz2"}
                :bar   {:nesty           {:mappy {:hi "there"
                                                 :stuff [1 2 {:how "areyou"} 3]}}
                        :junk            "thingz"}}
               cfg)))))
  
  (testing "An error is thrown if duplicate settings exist"
    (doseq [invalid-config-dir ["./test-resources/config/conflictdir1"
                                "./test-resources/config/conflictdir2"
                                "./test-resources/config/conflictdir3"]]
      (is (thrown-with-msg?
            IllegalArgumentException
            #"Duplicate configuration entry: \[:foo :baz\]"
            (bootstrap-services-with-cli-data [test-service] {:config invalid-config-dir}))))))