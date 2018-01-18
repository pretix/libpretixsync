libpretixsync
=============

.. image:: https://travis-ci.org/pretix/libpretixsync.svg?branch=master
   :target: https://travis-ci.org/pretix/libpretixsync

.. image:: https://codecov.io/gh/pretix/libpretixsync/branch/master/graph/badge.svg
   :target: https://codecov.io/gh/pretix/libpretixsync

This is a shared library between `pretixdroid`_ and `pretixdesk`_. It handles all core
business logic of the two applications and the synchronization primitives with the pretix server.

Release cycle
-------------

As we currently do not expect any third parties to use this library, we do not do formal releases
so far and do not upload the library to Maven repositories, but use it as a git submodule in our
other software to ease development. If you are interested in using this library for a new project,
please get in touch with us! :) We'll work something out.

Contributing
------------

If you like to contribute to this project, you are very welcome to do so. If you have any
questions in the process, please do not hesitate to ask us.

Please note that we have a `Code of Conduct`_
in place that applies to all project contributions, including issues, pull requests, etc.

License
-------
The code in this repository is published under the terms of the Apache License. 
See the LICENSE file for the complete license text.

This project is maintained by Raphael Michel <mail@raphaelmichel.de>. See the
AUTHORS file for a list of all the awesome folks who contributed to this project.

This project is 100 percent free and open source software. If you are interested in
commercial support, hosting services or supporting this project financially, please 
go to `pretix.eu`_ or contact Raphael directly.

.. _pretixdroid: https://github.com/pretix/pretixdroid
.. _pretixdesk: https://github.com/pretix/pretixdesk
.. _pretix.eu: https://pretix.eu
.. _Code of Conduct: https://docs.pretix.eu/en/latest/development/contribution/codeofconduct.html
