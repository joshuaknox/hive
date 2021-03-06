(ns hive.core
  (:gen-class)
  (:require [clojure.set :as set]
            [hive.board :as board]
            [hive.coord :as coord]))

(defn free-to-move? [board from to]
  "When going from a->b and flanked by c, d
   
   we are only free-to-move if the tallest of a, b (not including the beetle)
   is as least as tall as the shortest of c, d.
  "
 (let [direction (coord/sub to from)
       stack-size (partial board/stack-size board)
       a-b [(- (stack-size from) 1) (stack-size to)]
       c-d (map stack-size (coord/adjacent-coords from direction))]
  (>= (apply max a-b) (apply min c-d))))

; a board is a map {coord -> [piece]}

(defn grasshopper-moves [board coord]
  "Starting at coord, a grasshopper moves in a single direction until the first
   empty space. It must move over at least one occupied space"
  (keep (fn [direction]
          (when (contains? board (coord/add coord direction))
            (loop [coord coord]
              (if (contains? board coord)
                (recur (coord/add coord direction))
                coord))))
    coord/directions))

(defn beetle-moves [board coord]
  "The beetle may move one space in any direction, including occupied ones."
    (filter (every-pred (partial free-to-move? board coord)
                        (partial board/connected? (board/pop board coord)))
            (coord/neighbors coord)))

(defn queen-moves [board coord]
  "The queen may move in any unoccupied direction"
  (remove (partial contains? board) (beetle-moves board coord)))

(def pillbug-moves queen-moves)

(defn ant-moves [board coord]
  "Ants move like a queen but repeatedly.

   Do a BFS of available moves.
  "
  (loop [seen #{coord} queue (conj clojure.lang.PersistentQueue/EMPTY coord)]
    (if (empty? queue)
      (disj seen coord) ; you can't move to where you already were
      (let [node (peek queue)
            children (remove seen (queen-moves (board/move board coord node) node))]
        (recur (into seen children) (into (pop queue) children))))))

(defn spider-moves [board coord]
  "Spiders move like queens but exactly three spaces

   Do a BFS for all 3-length paths which don't backtrack (e.g. A -> B -> A)
   TODO: May a spider end where it started? The way this is implemented says yes.
  "
  (letfn [(explore-path [path]
            (for [move (queen-moves (board/move board coord (peek path)) (peek path))
                  :when (not= move (peek (pop path)))]
              (conj path move)))
          (search [depth paths]
           (if (= 3 depth)
               paths
               (recur (inc depth) (mapcat explore-path paths))))]
    (into #{} (map peek (search 0 [[coord]])))))

(defn ladybug-moves [board coord]
  "Ladybugs move up, then one along the top, then one down"
  (->> coord
       (board/occupied-neighbors board)
       (mapcat (partial board/occupied-neighbors (dissoc board coord)))
       (mapcat (partial board/unoccupied-neighbors board))
       (into #{})))

(declare available-moves)

(defn mosquito-moves [board coord]
  "The mosquito may move like any of the pieces surrounding it"
  (apply set/union
    (let [insects (distinct (map (comp :insect first board)
                                 (board/occupied-neighbors board coord)))]
      (for [insect insects :when (not= :mosquito insect)]
        (into #{} (available-moves board insect coord))))))

(defn available-moves [board insect coord]
  ((insect {:grasshopper grasshopper-moves
            :beetle beetle-moves
            :queen queen-moves
            :ant ant-moves
            :spider spider-moves
            :ladybug ladybug-moves
            :pillbug pillbug-moves
            :mosquito mosquito-moves})
    board coord))

(defn iterate-until-fixed [f initial]
  (loop [accum initial]
    (let [next-accum (f accum)]
      (if (= accum next-accum)
          accum
          (recur next-accum)))))

(defn one-hive-movable-pieces [board]
  "Pieces which connect two separate hives may not be moved, so:
     Pieces which have only one neighbor may move
     Pieces which are part of a cycle may move
       Except for those which are neighbors of a non-cyclic part of the board
  "
  (letfn [(num-neighbors [board coord] (count (board/occupied-neighbors board coord)))
          (leaf-nodes [board] (filter #(= 1 (num-neighbors board %))
                                      (keys board)))
          (without-leafs [board] (apply dissoc board (leaf-nodes board)))]
  (let [orig-leafs (leaf-nodes board)
        minimal-board (iterate-until-fixed without-leafs board)
        ; remove all coords which have a neighbor not in minimal board
        excluding-leaf-neighbors (filter #(every? minimal-board (board/occupied-neighbors board %))
                                   (keys minimal-board))]
    (into #{} (lazy-cat orig-leafs excluding-leaf-neighbors)))))

; the first move is a special case, player 1 spawns on the origin,
; player 2 spawns anywhere
(defn spawn-locations [board color]
  "Every position which is connected to the hive yet only next to pieces with :color color"
  (letfn [(stack-color [pieces] (:color (first pieces)))
          (emit-neighbors [[coord color]]
            (for [neighbor (board/unoccupied-neighbors board coord)]
              {color #{neighbor}}))]
  (let [colored-coords (map (fn [[coord pieces]] [coord (stack-color pieces)])
                            board)
        neighbors (mapcat emit-neighbors colored-coords)
        by-color (apply merge-with set/union neighbors)]
    (apply set/difference (by-color color) (vals (dissoc by-color color)))
)))

(defn pillbug-throws [board coord]
  "What are all the special moves the pillbug at coord can make?

   A pillbug may only move pieces which are not stacked and which won't break
   the hive by being moved. It may move pieces into any location adjacent to
   themselves.
  "
  (for [neighbor (board/occupied-neighbors board coord)
        empty-spot (board/unoccupied-neighbors board coord)
        :when (and (contains? (one-hive-movable-pieces board) neighbor)
                   (= 1 (board/stack-size board neighbor)))]
    [neighbor empty-spot]))

(defn -main
  "Plays Hive!"
  [& args]
  (println "Hello, World!"))
