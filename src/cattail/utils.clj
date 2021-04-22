(ns cattail.utils
  "Internal utilities used by Catty, not intended for external consumption."
  (:require [clojure.string :as str])
  (:import [java.lang.reflect Method Constructor Field Modifier]
           (android.content Context)))

(defmacro app-package-name
  "Allows other macros to hard-compile the name of application package in them."
  []
  (:catty.init/package-name *compiler-options*))

(defmacro memoized [inside-defn]
  (let [[_ name doc & fdecl] inside-defn
        m (assoc (meta name) :doc doc)]
    `(def ~(with-meta name m)
       (memoize (fn ~@fdecl)))))

(defn int-id
  "Makes an ID from arbitrary object by calling .hashCode on it.
  Returns the absolute value."
  [obj]
  (Math/abs (.hashCode ^Object obj)))

(defn simple-name
  "Takes a possibly package-qualified class name symbol and returns a
  simple class name from it."
  [full-class-name]
  (nth (re-find #"(.*\.)?(.+)" (str full-class-name)) 2))

(defn capitalize
  "Takes a string and upper-cases the first letter in it."
  [s]
  (str (.toUpperCase (subs s 0 1)) (subs s 1)))

(defn unicaseize
  "Takes a string lower-cases the first letter in it."
  [s]
  (str (.toLowerCase (subs s 0 1)) (subs s 1)))

(memoized
 (defn keyword->static-field
   "Takes a keyword and transforms it into a static field name.

   All letters in keyword are capitalized, and all dashes are replaced
   with underscores."
   [kw]
   (.toUpperCase (str/replace (name kw) \- \_))))

(defn keyword->camelcase
  "Takes a keyword and transforms its name into camelCase."
  [kw]
  (let [[first & rest] (str/split (name kw) #"-")]
    (str/join (cons first (map capitalize rest)))))

(memoized
 (defn keyword->setter
   "Takes a keyword and transforms it into a setter method name.

   Transforms keyword name into camelCase, capitalizes the first
   character and appends \"set\" at the beginning."
   [kw]
   (->> (keyword->camelcase kw)
        capitalize
        (str "set"))))

(defmacro call-if-nnil
  "Expands into check whether function is defined, then executes it
  and returns true or just returns false otherwise."
  [f & arguments]
  `(if ~f
     (do (~f ~@arguments)
         true)
     false))

;; Reflection functions

(defn class-or-type [cl]
  (condp = cl
    Boolean Boolean/TYPE
    Integer Integer/TYPE
    Long Integer/TYPE
    Double Double/TYPE
    Float Float/TYPE
    Character Character/TYPE
    cl))

(defn ^Method reflect-setter
  "Returns a Method object for the given UI object class, method name
  and the first argument type."
  [^Class widget-type, ^String method-name, ^Class value-type]
  (if-not (= widget-type Object)
    (let [value-type (class-or-type value-type)
          all-value-types (cons value-type (supers value-type))]
      (loop [[t & r] all-value-types]
        (if t
          (if-let [method (try
                            (.getDeclaredMethod widget-type method-name
                                                (into-array Class [t]))
                            (catch NoSuchMethodException e nil))]
            method
            (recur r))
          (reflect-setter (.getSuperclass widget-type)
                          method-name value-type))))
    (throw
     (NoSuchMethodException. (format "Couldn't find method .%s for argument %s)"
                                     method-name (.getName value-type))))))

(defn ^Constructor reflect-constructor
  "Returns a Constructor object for the given UI object class "
  [^Class widget-type constructor-arg-types]
  (.getConstructor widget-type (into-array Class constructor-arg-types)))

(defn reflect-field
  "Returns a field value for the given UI object class and the name of
  the field."
  [^Class widget-type, ^String field-name]
  (.get ^Field (.getDeclaredField widget-type field-name) nil))

(defn list-all-methods
  "Returns names of all non-private methods of a class, including methods
  declared in its parents."
  [^Class c]
  (loop [c c, methods ()]
    (if c
      (recur (.getSuperclass c)
             (concat methods
                     (keep (fn [^Method m]
                             (when-not (Modifier/isPrivate (.getModifiers m))
                               (.getName m)))
                           (.getDeclaredMethods c))))
      methods)))

(defn closest-android-ancestor
  [^Class c]
  (loop [c c]
    (when c
      (if-let [cand-name
               (re-find #"^class (android\..*)" (str c))]
        (Class/forName (second cand-name))
        (recur (.getSuperclass c))))))
