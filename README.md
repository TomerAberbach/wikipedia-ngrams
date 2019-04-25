# Wikipedia Ngrams

> A Kotlin project which extracts ngram counts from Wikipedia data dumps.

## Download

Download the latest jar from [releases](https://github.com/TomerAberbach/wikipedia-ngrams/releases).

You can also clone the repository and build with [maven](https://maven.apache.org/download.cgi):

```sh
$ git clone https://github.com/TomerAberbach/wikipedia-ngrams.git
$ cd wikipedia-ngrams
$ mvn package
```

A fat jar called `wikipedia-ngrams-VERSION-jar-with-dependencies.jar` will be in a newly created `target` directory.

## Usage

DISCLAIMER: Many of these commands will take a very long time to run.

Download the latest [Wikipedia data dump](https://meta.wikimedia.org/wiki/Data_dumps/Download_tools) using `wget`:

```sh
$ wget -np -nd -c -A 7z https://dumps.wikimedia.org/metawiki/latest/metawiki-latest-pages-meta-current.xml.bz2
```

Or using `axel`:

```sh
$ axel --num-connections=3 https://dumps.wikimedia.org/metawiki/latest/metawiki-latest-pages-meta-current.xml.bz2
```

To speed up the download you should replace `https://dumps.wikimedia.org` with the [mirror](https://meta.wikimedia.org/wiki/Mirroring_Wikimedia_project_XML_dumps) closest to you.

Once downloaded, extract the zipped data using a tool like `lbzip2` and feed the resulting `enwiki-latest-pages-articles.xml` file into [WikiExtractor](https://github.com/attardi/wikiextractor):

```sh
$ python3 WikiExtractor.py --no_templates --json enwiki-latest-pages-articles.xml
```

This will output a large directory structure with root directory `text`.

Finally, run `wikipedia-ngrams.jar` with the desired ngram "n" (2 in this example) and the path to directory output of [WikiExtractor](https://github.com/attardi/wikiextractor):

```sh
$ java -jar wikipedia-ngrams.jar 2 text
```

Note that you may need to increase the maximum heap size and/or disable GC overhead limit.

`contexts.txt` and `2-grams.txt` files will be in an `out` directory. `contexts.txt` caches the "sentences" in the Wikipedia data dump. To use this cache in your next run (with n = 3 for example), run the following command:

```sh
$ java -jar wikipedia-ngrams.jar 3 out/contexts.txt
```

The outputted files will not be sorted. Use a command-line tool like `sort` to do so.

## Dependencies

* [Stanford CoreNLP](https://stanfordnlp.github.io/CoreNLP/index.html)
* [fastutil](http://fastutil.di.unimi.it)

## Contributing

Pull requests and stars are always welcome. For bugs and feature requests, [please create an issue](https://github.com/TomerAberbach/wikipedia-ngrams/issues/new). `OutOfMemoryError` is not a legitimate issue. The burden is on the user to allocate enough heap space and have a large enough RAM (consider allocating a larger [swap file](https://linuxize.com/post/create-a-linux-swap-file)).

## Author

**Tomer Aberbach**

* [Github](https://github.com/TomerAberbach)
* [NPM](https://www.npmjs.com/~tomeraberbach)
* [LinkedIn](https://www.linkedin.com/in/tomer-a)
* [Website](https://tomeraberba.ch)

## License

Copyright © 2019 [Tomer Aberbach](https://github.com/TomerAberbach)
Released under the [MIT license](https://github.com/TomerAberbach/wikipedia-ngrams/blob/master/LICENSE).