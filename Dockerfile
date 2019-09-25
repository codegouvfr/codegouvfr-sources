# Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
# SPDX-License-Identifier: EPL-2.0
# License-Filename: LICENSES/EPL-2.0.txt

FROM java:8-alpine
ENV SMTP_HOST ${SMTP_HOST}
ENV SMTP_LOGIN ${SMTP_LOGIN}
ENV SMTP_PASSWORD ${SMTP_PASSWORD}
ENV CODEGOUVFR_ADMIN_EMAIL ${CODEGOUVFR_ADMIN_EMAIL}
ENV CODEGOUVFR_FROM ${CODEGOUVFR_FROM}
ENV CODEGOUVFR_PORT ${CODEGOUVFR_PORT}
ENV CODEGOUVFR_MSGID_DOMAIN ${CODEGOUVFR_MSGID_DOMAIN}
ADD target/codegouvfr-standalone.jar /codegouvfr/codegouvfr-standalone.jar
CMD ["java", "-jar", "/codegouvfr/codegouvfr-standalone.jar"]
