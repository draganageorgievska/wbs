package mk.finki.ukim.wbs.web.controller;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Controller
public class MusicianController {
    private final String SPARQLEndpoint="https://dbpedia.org/sparql";
    @GetMapping("/")
    public String index(Model model){
        String queryString="select distinct ?genre str(?genreName) where {?genre <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/Genre> ;" +
                "<http://www.w3.org/2000/01/rdf-schema#label> ?genreName " +
                "filter(lang(?genreName)=\"en\")} " +
                "order by asc(UCASE(str(?genreName))) limit 9800";
        Query query= QueryFactory.create(queryString);
        List<String[]> genres=new ArrayList<>();
        try(QueryExecution qexec = QueryExecutionFactory.sparqlService(SPARQLEndpoint,query)){
            ResultSet resultSet=qexec.execSelect();
            while(resultSet.hasNext()){
                QuerySolution soln=resultSet.nextSolution();
                String genre[] = new String[2];
                genre[0]=String.valueOf(soln.get("callret-1"));
                genre[1]= String.valueOf(soln.get("genre"));
                genres.add(genre);
            }
        }
        model.addAttribute("genres",genres);
        return "index";
    }
    @PostMapping("/search")
    public String search(@RequestParam(required = false) String name, @RequestParam(required = false) String album, @RequestParam(required = false) String genre, Model model) throws IOException {
        String queryString="select distinct str(?name) str(?bio) ?artist ?thumbnail where {" +
                "{select ?artist where{ ?artist <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/MusicalArtist> }} union " +
                "{ select ?artist where {?artist <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://schema.org/MusicGroup> }} " +
                "?artist <http://www.w3.org/2000/01/rdf-schema#label> ?name ;" +
                "<http://www.w3.org/2000/01/rdf-schema#comment> ?bio ;" +
                "<http://dbpedia.org/ontology/thumbnail> ?thumbnail ";
        List<Object[]> solutions=new ArrayList<>();
        name=name.toUpperCase();
       if(album.equals("")||album==null) {
           if (!genre.equals("")) {
               queryString += "; <http://dbpedia.org/ontology/genre> <" + genre + ">  ";
           }
           if(!name.equals("")){
               queryString+="filter(regex(ucase(?name),\""+name+"\")) ";
           }
           queryString+="filter(lang(?name)=\"en\")" +
                   "filter(lang(?bio)=\"en\") } limit 50";
           Query query= QueryFactory.create(queryString);
           ResultSet resultSet;
           try(QueryExecution qexec = QueryExecutionFactory.sparqlService(SPARQLEndpoint,query)){
               resultSet=qexec.execSelect();
           }
               while(resultSet.hasNext()){
                   Object[] answers = new Object[7];
                   QuerySolution soln=resultSet.nextSolution();
                   name= String.valueOf(soln.get("artist"));
                   answers[0]= String.valueOf(soln.get("callret-0"));
                   answers[1]= String.valueOf(soln.get("callret-1"));
                   answers[2]= String.valueOf(soln.get("?thumbnail"));
                   String artist= name.replace("resource","data").replace("http","https");
                   URL url=new URL(artist+".ttl");
                   HttpURLConnection huc=(HttpURLConnection) url.openConnection();
                   int responseCode=huc.getResponseCode();
                   if(HttpURLConnection.HTTP_OK==responseCode) {
                   org.apache.jena.rdf.model.Model model1 = ModelFactory.createDefaultModel();
                       InputStream in = FileManager.get().open(artist + ".ttl");
                       model1.read(in, "", "TURTLE");
                       Property genr = model1.getProperty("http://dbpedia.org/ontology/genre");
                       StmtIterator genreIterator = model1.listStatements(new SimpleSelector(null, genr, (RDFNode) null));
                       List<String> genres = new ArrayList<>();
                       while (genreIterator.hasNext()) {
                           String query1 = "select str(?name) where { <" +
                                   genreIterator.nextStatement().getObject() + "> <http://www.w3.org/2000/01/rdf-schema#label> ?name " +
                                   "filter(lang(?name)=\"en\")}";
                           query = QueryFactory.create(query1);
                           ResultSet res;
                           try (QueryExecution qexec = QueryExecutionFactory.sparqlService(SPARQLEndpoint, query)) {
                               res = qexec.execSelect();
                           }
                           if(res.hasNext()) {
                               genres.add(String.valueOf(res.nextSolution().get("callret-0")));
                           }
                       }
                       answers[3] = genres;
                       Property occupation = model1.getProperty("http://dbpedia.org/property/occupation");
                       StmtIterator occupationIterator = model1.listStatements(new SimpleSelector(null, occupation, (RDFNode) null));
                       List<Object> it=new ArrayList<>();
                       while(occupationIterator.hasNext()){
                           Statement statement=occupationIterator.nextStatement();
                           if(statement.getObject().isResource()) {
                               String query1 = "select str(?name) where { <" +
                                       statement.getObject() + "> <http://www.w3.org/2000/01/rdf-schema#label> ?name " +
                                       "filter(lang(?name)=\"en\")}";
                               query = QueryFactory.create(query1);
                               ResultSet res;
                               try (QueryExecution qexec = QueryExecutionFactory.sparqlService(SPARQLEndpoint, query)) {
                                   res = qexec.execSelect();
                               }
                               if (res.hasNext()) {
                                   it.add(res.nextSolution().get("callret-0"));
                               }
                           }
                           else if(!statement.getObject().toString().equals("@en")){
                               String[] temp=statement.getObject().toString().split("@");
                               it.add(temp[0]);
                           }
                       }
                       answers[4] = it;
                       queryString = "select distinct str(?name) str(?releaseDate) where{" +
                               " ?album <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/Album> ; " +
                               "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/MusicalWork> ; " +
                               "<http://dbpedia.org/property/artist> <" + name + "> ;" +
                               "<http://www.w3.org/2000/01/rdf-schema#label> ?name ; " +
                               "<http://dbpedia.org/property/released> ?releaseDate " +
                               "filter(lang(?name)=\"en\") }";
                       query = QueryFactory.create(queryString);
                       try (QueryExecution qexec = QueryExecutionFactory.sparqlService(SPARQLEndpoint, query)) {
                           ResultSet result = qexec.execSelect();
                           List<String> names = new ArrayList<>();
                           List<String> dates = new ArrayList<>();
                           while (result.hasNext()) {
                               QuerySolution solution = result.nextSolution();
                               names.add(String.valueOf(solution.get("callret-0")));
                               dates.add(String.valueOf(solution.get("callret-1")));
                           }
                           answers[5] = names;
                           answers[6] = dates;
                       }

                       solutions.add(answers);
                   }
           }
       }
       else{
           album=album.toUpperCase();
           ResultSet result;
           Query query;
           String albumQuery = "select distinct ?album str(?releaseDate) ?artist str(?name) str(?artistName) str(?bio) where {" +
                           "?album <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/Album> ;" +
                           "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/MusicalWork> ;" +
                           "<http://dbpedia.org/property/released> ?releaseDate ; " +
                           "<http://www.w3.org/2000/01/rdf-schema#label> ?name ; " +
                           "<http://dbpedia.org/property/artist> ?artist ";
                   if (!genre.equals("")) {
                       albumQuery += "; <http://dbpedia.org/property/genre> <" + genre + "> ";
                   }
                   albumQuery+=". ?artist <http://www.w3.org/2000/01/rdf-schema#label> ?artistName ;" +
                           " <http://www.w3.org/2000/01/rdf-schema#comment> ?bio ";
           if(!name.equals("")){
               albumQuery+= "filter(regex(ucase(?artistName),\""+name+"\"))";
           }
                   albumQuery+="filter(lang(?artistName)=\"en\") " +
                           "filter(lang(?bio)=\"en\")" +
                           "filter(lang(?name)=\"en\") " +
                           "filter(regex(ucase(?name),\""+album+"\")) }limit 50";
                   query= QueryFactory.create(albumQuery);
                   try(QueryExecution qexec = QueryExecutionFactory.sparqlService(SPARQLEndpoint,query)){
                       result=qexec.execSelect();
                   }
                   while(result.hasNext()) {
                       QuerySolution solution = result.nextSolution();
                       Object[] answers = new Object[7];
                       if (String.valueOf(solution.get("artist")).startsWith("http")) {
                           String artist = String.valueOf(solution.get("artist")).replace("resource", "data").replace("http", "https");
                           URL url = new URL(artist + ".ttl");
                           HttpURLConnection huc = (HttpURLConnection) url.openConnection();
                           int responseCode = huc.getResponseCode();
                           if (HttpURLConnection.HTTP_OK == responseCode) {
                               org.apache.jena.rdf.model.Model model1 = ModelFactory.createDefaultModel();
                               InputStream in = FileManager.get().open(artist + ".ttl");
                               model1.read(in, "", "TURTLE");
                               answers[0]=solution.get("callret-4");
                               answers[1]=solution.get("callret-5");
                               Property property = model1.getProperty("http://dbpedia.org/ontology/thumbnail");
                               StmtIterator iterator = model1.listStatements(new SimpleSelector(null, property, (RDFNode) null));
                               if (iterator.hasNext()) {
                                   answers[2] = iterator.nextStatement().getObject();
                               }
                               property = model1.getProperty("http://dbpedia.org/ontology/genre");
                               iterator = model1.listStatements(new SimpleSelector(null, property, (RDFNode) null));
                               List<String> genres = new ArrayList<>();
                               while (iterator.hasNext()) {
                                   String query1 = "select str(?name) where { <" +
                                           iterator.nextStatement().getObject() + "> <http://dbpedia.org/property/name> ?name }";
                                   query = QueryFactory.create(query1);
                                   ResultSet res;
                                   try (QueryExecution qexec = QueryExecutionFactory.sparqlService(SPARQLEndpoint, query)) {
                                       res = qexec.execSelect();
                                   }
                                   if(res.hasNext()) {
                                       genres.add(String.valueOf(res.nextSolution().get("callret-0")));
                                   }
                               }
                               answers[3] = genres;
                               property = model1.getProperty("http://dbpedia.org/property/occupation");
                               iterator = model1.listStatements(new SimpleSelector(null, property, (RDFNode) null));
                               List<Object> it=new ArrayList<>();
                               while(iterator.hasNext()){
                                   Statement statement=iterator.nextStatement();

                                   if(statement.getObject().isResource()) {
                                       String query1 = "select str(?name) where { <" +
                                               statement.getObject() + "> <http://www.w3.org/2000/01/rdf-schema#label> ?name " +
                                               "filter(lang(?name)=\"en\")}";
                                       query = QueryFactory.create(query1);
                                       ResultSet res;
                                       try (QueryExecution qexec = QueryExecutionFactory.sparqlService(SPARQLEndpoint, query)) {
                                           res = qexec.execSelect();
                                       }
                                       if (res.hasNext()) {
                                           it.add(res.nextSolution().get("callret-0"));
                                       }
                                   }
                                   else if(!statement.getObject().toString().equals("@en")){
                                       String[] temp=statement.getObject().toString().split("@");
                                       it.add(temp[0]);
                                   }
                               }
                               answers[4] = it;
                               answers[5] = solution.get("callret-3");
                               answers[6]=solution.get("callret-1");
                               solutions.add(answers);
                           }
                       }
                   }
       }
       model.addAttribute("results", solutions);
        return "result";
    }
    @PostMapping("/associatedArtist")
    public String associatedArtistSearch(@RequestParam String artist, @RequestParam(required = false) String genre,Model model) throws IOException{
        artist = artist.toUpperCase();
        String queryString = "select distinct ?ogArtist str(?artistName) where{" +
                "{select ?ogArtist where{ ?ogArtist <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/MusicalArtist> }} union " +
            "{ select ?ogArtist where {?ogArtist <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://schema.org/MusicGroup> }} " +
                "?ogArtist <http://dbpedia.org/ontology/associatedMusicalArtist> ?artist ; " +
                "<http://dbpedia.org/property/name> ?artistName ";
        if (!genre.equals("")) {
            queryString += ". ?artist <http://dbpedia.org/ontology/genre> <" + genre + "> ";
        }
        queryString += "filter(regex(ucase(str(?artistName)),\"" + artist + "\")) } ";
        ResultSet resultSet;
        ResultSet result;
        List<Object> resultsList = new ArrayList<>();
        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(SPARQLEndpoint, query)) {
            resultSet = qexec.execSelect();
            while (resultSet.hasNext()) {

                QuerySolution soln = resultSet.nextSolution();
                model.addAttribute("artist",String.valueOf(soln.get("callret-1")));
                String qs="select distinct ?artist str(?name) str(?bio) ?thumbnail where{ <" +
                        soln.get("ogArtist")+"> <http://dbpedia.org/ontology/associatedMusicalArtist> ?artist . " +
                        "?artist <http://www.w3.org/2000/01/rdf-schema#label> ?name ; " +
                        "<http://www.w3.org/2000/01/rdf-schema#comment> ?bio ; " +
                        "<http://dbpedia.org/ontology/thumbnail> ?thumbnail " +
                        "filter(lang(?name)=\"en\") " +
                        "filter(lang(?bio)=\"en\") }";
                Query q = QueryFactory.create(qs);
                try (QueryExecution qx = QueryExecutionFactory.sparqlService(SPARQLEndpoint, q)) {
                    result = qx.execSelect();
                }
                while(result.hasNext()){
                    QuerySolution solu=result.nextSolution();
                Object[] answers = new Object[7];
                answers[0] = String.valueOf(solu.get("callret-1"));
                answers[1] = String.valueOf(solu.get("callret-2"));
                answers[2] = String.valueOf(solu.get("thumbnail"));
                String assocArtist = String.valueOf(solu.get("artist")).replace("resource", "data").replace("http", "https");
                URL url = new URL(assocArtist + ".ttl");
                HttpURLConnection huc = (HttpURLConnection) url.openConnection();
                int responseCode = huc.getResponseCode();
                if (HttpURLConnection.HTTP_OK == responseCode) {
                    org.apache.jena.rdf.model.Model model1 = ModelFactory.createDefaultModel();
                    InputStream in = FileManager.get().open(assocArtist + ".ttl");
                    model1.read(in, "", "TURTLE");
                    Property property = model1.getProperty("http://dbpedia.org/ontology/genre");
                    StmtIterator iterator = model1.listStatements(new SimpleSelector(null, property, (RDFNode) null));
                    List<String> genres = new ArrayList<>();
                    while (iterator.hasNext()) {
                        String query1 = "select str(?name) where { <" +
                                iterator.nextStatement().getObject() + "> <http://dbpedia.org/property/name> ?name }";
                        query = QueryFactory.create(query1);
                        ResultSet res;
                        try (QueryExecution qexe = QueryExecutionFactory.sparqlService(SPARQLEndpoint, query)) {
                            res = qexe.execSelect();
                        }
                        if (res.hasNext()) {
                            genres.add(String.valueOf(res.nextSolution().get("callret-0")));
                        }
                    }
                    answers[3] = genres;
                    property = model1.getProperty("http://dbpedia.org/property/occupation");
                    iterator = model1.listStatements(new SimpleSelector(null, property, (RDFNode) null));
                    List<Object> it = new ArrayList<>();
                    while (iterator.hasNext()) {
                        Statement statement = iterator.nextStatement();

                        if (statement.getObject().isResource()) {
                            String query1 = "select str(?name) where { <" +
                                    statement.getObject() + "> <http://www.w3.org/2000/01/rdf-schema#label> ?name " +
                                    "filter(lang(?name)=\"en\")}";
                            query = QueryFactory.create(query1);
                            ResultSet res;
                            try (QueryExecution qeec = QueryExecutionFactory.sparqlService(SPARQLEndpoint, query)) {
                                res = qeec.execSelect();
                            }
                            if (res.hasNext()) {
                                it.add(res.nextSolution().get("callret-0"));
                            }
                        } else if (!statement.getObject().toString().equals("@en")) {
                            String[] temp = statement.getObject().toString().split("@");
                            it.add(temp[0]);
                        }
                    }
                    answers[4] = it;
                    queryString = "select distinct str(?name) str(?releaseDate) where{" +
                            " ?album <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/Album> ; " +
                            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/MusicalWork> ; " +
                            "<http://dbpedia.org/property/artist> <" + solu.get("artist") + "> ;" +
                            "<http://www.w3.org/2000/01/rdf-schema#label> ?name ; " +
                            "<http://dbpedia.org/property/released> ?releaseDate " +
                            "filter(lang(?name)=\"en\") }";
                    query = QueryFactory.create(queryString);
                    try (QueryExecution qxec = QueryExecutionFactory.sparqlService(SPARQLEndpoint, query)) {
                        ResultSet result1 = qxec.execSelect();
                        List<String> names = new ArrayList<>();
                        List<String> dates = new ArrayList<>();
                        while (result1.hasNext()) {
                            QuerySolution solution = result1.nextSolution();
                            names.add(String.valueOf(solution.get("callret-0")));
                            dates.add(String.valueOf(solution.get("callret-1")));
                        }
                        answers[5] = names;
                        answers[6] = dates;
                    }
                    resultsList.add(answers);
                }
                }
                if(resultsList.size()>0){
                    break;
                }
            }
            model.addAttribute("results", resultsList);
        }
        return "result";
    }
}
