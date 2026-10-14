# The office of the sample: LibreOffice alone, without Java nor application, listening for UNO
# connections. The application connects to it with the attached manager of JODConverter.
#
# The steps follow the "Running in Containers" page of the JODConverter documentation:
# https://jodconverter.github.io/jodconverter/latest/getting-started/containers/
FROM debian:trixie-slim

# - tini, the init process: it reaps the helper processes of LibreOffice.
# - The modules of LibreOffice for each family of documents (text, spreadsheet, presentation,
#   drawing), in their version without graphical interface.
# - The fonts that are metric-compatible with the usual Microsoft fonts (Arial, Times New Roman,
#   Courier New, Calibri, Cambria), and the hyphenation dictionary of English: add those of the
#   languages and scripts of your documents.
#
# LibreOffice comes from trixie-backports (26.x): the version of trixie itself (25.2.3) crashes when
# it loads a text, a presentation or a drawing sent through the connection, which is how the
# documents reach an office in a container.
RUN echo "deb http://deb.debian.org/debian trixie-backports main" \
      > /etc/apt/sources.list.d/backports.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends tini hyphen-en-us \
      fonts-liberation fonts-crosextra-carlito fonts-crosextra-caladea \
 && apt-get install -y --no-install-recommends -t trixie-backports \
      libreoffice-writer-nogui libreoffice-calc-nogui libreoffice-impress-nogui libreoffice-draw-nogui \
 && rm -rf /var/lib/apt/lists/*

# A UTF-8 locale.
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8

# Not root: the office runs as this user, whose home holds its profile.
RUN useradd --system --create-home --uid 10001 office
USER office
WORKDIR /home/office

EXPOSE 2002

# The office accepts UNO connections on port 2002 of every interface of the container, so that the
# port can be published (compose.yaml publishes it on this machine only). Whoever can connect to it
# controls the office: never publish it beyond the applications that use it.
ENTRYPOINT ["/usr/bin/tini", "--", \
  "soffice", "--headless", "--invisible", "--nocrashreport", "--nodefault", "--nofirststartwizard", \
  "--nologo", "--norestore", "--nolockcheck", \
  "--accept=socket,host=0.0.0.0,port=2002,tcpNoDelay=1;urp;StarOffice.ServiceManager", \
  "-env:UserInstallation=file:///home/office/profile"]
