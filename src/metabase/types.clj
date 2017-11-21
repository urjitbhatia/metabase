(ns metabase.types
  "The Metabase Hierarchical Type System (MHTS). This is a hierarchy where types derive from one or more parent types,
   which in turn derive from their own parents. This makes it possible to add new types without needing to add
   corresponding mappings in the frontend or other places. For example, a Database may want a type called something
   like `:type/CaseInsensitiveText`; we can add this type as a derivative of `:type/Text` and everywhere else can
   continue to treat it as such until further notice.

   To add custom derivative types at runtime, you can use the `add-custom-type!` function, which adds it via the
   normal Clojure `derive` mechanism, but also records the mapping in a Setting so it can be preserved after server
   restarts. Reload any custom mappings with `reload-custom-types!` before doing things like `isa?` checks to make
   sure these custom types are in place."
  (:require [metabase.models.setting :refer [defsetting]]
            [metabase.util :as u]))

(derive :type/Collection :type/*)

(derive :type/Dictionary :type/Collection)
(derive :type/Array :type/Collection)

;;; Numeric Types

(derive :type/Number :type/*)

(derive :type/Integer :type/Number)
(derive :type/BigInteger :type/Integer)
(derive :type/ZipCode :type/Integer)

(derive :type/Float :type/Number)
(derive :type/Decimal :type/Float)

(derive :type/Coordinate :type/Float)
(derive :type/Latitude :type/Coordinate)
(derive :type/Longitude :type/Coordinate)


;;; Text Types

(derive :type/Text :type/*)

(derive :type/UUID :type/Text)

(derive :type/URL :type/Text)
(derive :type/AvatarURL :type/URL)
(derive :type/ImageURL :type/URL)

(derive :type/Email :type/Text)

(derive :type/City :type/Text)
(derive :type/State :type/Text)
(derive :type/Country :type/Text)

(derive :type/Name :type/Text)
(derive :type/Description :type/Text)

(derive :type/SerializedJSON :type/Text)
(derive :type/SerializedJSON :type/Collection)

(derive :type/PostgresEnum :type/Text)

;;; DateTime Types

(derive :type/DateTime :type/*)

(derive :type/Time :type/DateTime)
(derive :type/Date :type/DateTime)

(derive :type/UNIXTimestamp :type/DateTime)
(derive :type/UNIXTimestamp :type/Integer)
(derive :type/UNIXTimestampSeconds :type/UNIXTimestamp)
(derive :type/UNIXTimestampMilliseconds :type/UNIXTimestamp)


;;; Other

(derive :type/Boolean :type/*)
(derive :type/Enum :type/*)

;;; Text-Like Types: Things that should be displayed as text for most purposes but that *shouldn't* support advanced filter options like starts with / contains

(derive :type/TextLike :type/*)
(derive :type/IPAddress :type/TextLike)
(derive :type/MongoBSONID :type/TextLike)

;;; "Virtual" Types

(derive :type/Address :type/*)
(derive :type/City :type/Address)
(derive :type/State :type/Address)
(derive :type/Country :type/Address)
(derive :type/ZipCode :type/Address)


;;; Legacy Special Types. These will hopefully be going away in the future when we add columns like `:is_pk` and
;;; `:cardinality`

(derive :type/Special :type/*)

(derive :type/FK :type/Special)
(derive :type/PK :type/Special)

(derive :type/Category :type/Special)

(derive :type/City :type/Category)
(derive :type/State :type/Category)
(derive :type/Country :type/Category)
(derive :type/Name :type/Category)


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                  CUSTOM TYPES                                                  |
;;; +----------------------------------------------------------------------------------------------------------------+

(defsetting ^:private custom-types
  "Map of custom `child-type` to its `parent-type`. This map is reloaded during runtime which allows drivers or other
   custom code to add new types to the Metabase Hierarchical Type System (MHTS) at runtime. For example, the Postgres
   driver uses this feature to define enum types it encounters as derivatives of `:type/PostgresEnum`. An enum type
   named `color` is registered as a custom type called `:type/PostgresEnum.color`; the Postgres driver can then use
   this info when processing queries to make sure the correct cast (`::color`) is applied to values in `WHERE`
   clauses.

   This Setting is used to record the various custom types that get registered at one point or another so we can be
   sure they're present when the type hierarchy is introspected after a server restart. This means you can register
   new types in code pathways that aren't activated often (for example, the sync process) and still rely on them being
   present in other parts of the codebase."
  :type      :json
  :internal? true)

(defn reload-custom-types!
  "Make sure any dynamically-defined custom types added at runtime to the MHTS are added back to Clojure's type
   hierarchy (via `derive`). Make sure you call this before exporting the entire type hierachy to consumers like
   the frontend, and before making calls to `isa?`."
  []
  ;; The call to (custom-types) will fail if the DB isn't set up yet, so in that case just ignore exceptions which
  ;; effectively turns this into a no-op.
  (doseq [[child parent] (u/ignore-exceptions (custom-types))]
    (derive (keyword child) (keyword parent))))

(defn add-custom-type!
  "Add a new custom type to the MHTS. `child` will be a derivative of `parent.` Use this to dynamically define types
   at runtime, for example:

     ;; dynamically define a new type for Postgres enum 'color'
     (add-custom-type :type/PostgresEnum.color :type/PostgresEnum)"
  [child parent]
  (custom-types (assoc (custom-types) child parent)) ; record the new type in the Setting
  (derive (keyword child) (keyword parent))) ; now derive the type so Clojure knows about it


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                    UTIL FNS                                                    |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn types->parents
  "Return a map of various types to their parent types.
   This is intended for export to the frontend as part of `MetabaseBootstrap` so it can build its own implementation
   of `isa?`."
  []
  (reload-custom-types!)
  (into {} (for [t (descendants :type/*)]
             {t (parents t)})))
