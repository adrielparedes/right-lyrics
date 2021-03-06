# Right Lyrics

A very simple microservice architecture to be deployed in OpenShift.

## Overview

![preview](./preview.png)

## Topology

In OpenShift the application can be seen as follows with the Developer perspective:

![topology](./topology.png)

## Components

* Lyrics Page (React.js + NGINX)
* Lyrics Service (Node.js + MongoDB)
* Songs Service (Spring Boot + PostgreSQL)
* Hits Service (Python + Redis)
* Operator (Ansible)

## Deploy in OpenShift

Create the CRD so the Operator knows about the CR that will be watching (this requires cluster admin privileges):

```bash
oc create -f ./operator/deploy/crds/veicot_v1_rightlyrics_crd.yaml
```

Create the Right Lyrics project:

```bash
oc new-project right-lyrics
```

Deploy the Operator:

```bash
oc create -f ./operator/deploy/service_account.yaml -n right-lyrics
oc create -f ./operator/deploy/role.yaml -n right-lyrics
oc create -f ./operator/deploy/role_binding.yaml -n right-lyrics
oc create -f ./operator/deploy/operator.yaml -n right-lyrics
```

Deploy a CR:

```yaml
apiVersion: veicot.io/v1
kind: RightLyrics
metadata:
  name: my-rightlyrics
spec:
  lyricsPageReplicas: 1
  lyricsServiceReplicas: 1
  songsServiceReplicas: 1
  hitsServiceReplicas: 1
```

The CR can be created as follows:

```bash
oc create -f ./operator/deploy/crds/veicot_v1_rightlyrics_cr.yaml -n right-lyrics
```

Finally the Operator watches this CR and creates the application.

## Service Mesh

This section is a work in progress.

### Prerequisites

* A Service Mesh Control Plane
* A Service Mesh Member Roll configured to watch the Right Lyrics namespace

### Notes

* Automatic Envoy sidecar injection is configured via annotations for all deployments
* A basic configuration can be found in [this](istio.yaml) file

### Service Graph in Kiali

The following topology can be seen in Kiali when traffic arrives to the Service Mesh:

![kiali](./kiali.png)

## Contributing

To contribute please visit [this](CONTRIBUTING.md) section.
