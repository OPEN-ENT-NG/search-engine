# À propos de l'application moteur de recherche

* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright Région Hauts-de-France (ex Picardie)

* Développeur(s) : ATOS, Edifice

* Financeur(s) : Région Hauts-de-France  (ex Picardie)

* Description : Moteur de recherche sur l'ensemble des applications de l'ENT.

# Documentation technique

## Prérequis
L'application moteur de recherche utilise le pattern "Publication/Souscription". Les applications de l'ENT ont donc la responsabilité d'effectuer une souscription par l’intermédiaire d'un appel à une méthode ENT-CORE ("setSearchingEvents") et de fournir le comportement de recherche désiré (implémentation de la logique de recherche de l'application).

## Construction

<pre>
		gradle copyMod
</pre>

## Déployer dans ent-core


## Configuration

Dans le fichier `/search-engine/deployment/searchengine/conf.json.template` :

Configurer l'application de la manière suivante :
<pre>
{
      "name": "fr.openent~search-engine~0.1-SNAPSHOT",
      "config": {
        "main" : "fr.openent.searchengine.SearchEngine",
        "port" : 8053,
        "app-name" : "Searchengine",
    	"app-address" : "/searchengine",
    	"app-icon" : "searchengine-large",
        "host": "${host}",
        "ssl" : $ssl,
        "auto-redeploy": false,
        "userbook-host": "${host}",
        "integration-mode" : "HTTP",
        "app-registry.port" : 8012,
        "mode" : "${mode}",
        "entcore.port" : 8009,
        "max-sec-time-allowed" : 4,
        "paging-size-per-collection" : 10,
        "search-word-min-size" : 4
}
</pre>
Les paramètres de configurations suivant peuvent être omis et comportent les valeurs par défaut spécifiées ci-dessus (Extrait de configuration) :

 - "max-sec-time-allowed": Entier qui indique le nombre de seconde toléré pour mener à bien une requête de recherche. Si le temps indiqué est dépassé, le moteur de recherche transmet un résultat partiel.

 - "paging-size-per-collection": Entier qui représente le nombre d'occurrence recherché dans chacune des applications. La taille d'un résultat de recherche (une page) correspond donc à ce nombre multiplié par le nombre d'application candidate à la recherche.

 - "search-word-min-size" : Entier qui détermine la taille minimale des mots retenus pour effectuer la recherche.

Associer une route d'entée à la configuration du module proxy intégré (`"name": "fr.openent~search-engine~0.1-SNAPSHOT"`) :
<pre>
	{
		"location": "/searchengine",
		"proxy_pass": "http://localhost:8053"
	}
</pre>



# Présentation du module

## Fonctionnalités

Il permet de chercher des ressources sur l'ensemble des applications qui ont souscrit à la recherche.

La recherche s'effectue seulement sur des données inscrites dans une base de données (NoSQL ou Relationnelle).

Les résultats disponibles correspondent à la fois aux critères de recherche saisis (Mots saisis à rechercher et Applications sélectionnées pour la recherche) et à vos droits d’accès (seules les ressources au moins accessibles en lecture sont présentées)

Les résultats sont constitués des informations principales suivantes : un titre, une description et un lien permettant d'accéder à la ressource.  En sus, des informations sur la dernière date de modification et le propriétaire de la ressource sont disponibles.

## Modèle serveur

Le module serveur utilise un contrôleur et implémente des mécanismes de communication (Point-to-Point, Publish-subscribe) via le bus vert.x.

Le contrôleur`SearchEngineController` correspond au point d'entrée de l'application, il permet notamment l'établissement de :
 * L'exposition des micro-services REST,
 * La sécurité globale

Le contrôleur étend les classes du framework Ent-core.

Un jsonschema (search.json) permet de vérifier les données reçues par le serveur et il se trouve dans le dossier "src/main/resources/jsonschema".

Un mécanisme de pagination côté serveur est mis en œuvre et repose sur les mécanismes de pagination des moteurs de bases de données des applications candidates (applications qui ont souscrit).

## Modèle front-end

Le modèle Front-end manipule trois objets "models" `Search, SearchType et SearchField` et fournit deux collections (listes) d'objet `searchs` (liste des résultats de la recherche) et `searchTypes`(liste des applications candidates). Il met en œuvre un mécanisme de pagination par agrégation des données.

Le contrôleur `SearchEngine` assure le routage des URLs de type (#)  et l'exposition des données et comportements nécessaire aux vues.
