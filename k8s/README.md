# PatentFlow BE EKS Deployment

## Flow

```text
main push
-> GitHub Actions builds a Docker image
-> GitHub Actions pushes the image to Harbor with the commit SHA tag
-> GitHub Actions connects to EKS with kubeconfig
-> GitHub Actions applies Kubernetes manifests
-> GitHub Actions updates the Deployment image tag
-> EKS pulls the new image from Harbor and rolls out new Pods
```

Harbor stores images. It does not update EKS by itself. The deployment update is triggered by GitHub Actions through `kubectl`.

## GitHub Secrets

Set these secrets in the GitHub repository:

```text
HARBOR_REGISTRY
HARBOR_PROJECT
HARBOR_USERNAME
HARBOR_PASSWORD
KUBE_CONFIG
SPRING_DATASOURCE_PASSWORD
PATENTFLOW_JWT_SECRET
PATENTFLOW_BOOTSTRAP_ADMIN_USERNAME
PATENTFLOW_BOOTSTRAP_ADMIN_PASSWORD
PATENTFLOW_BOOTSTRAP_ADMIN_DISPLAY_NAME
```

Optional secrets:

```text
GMAIL_USERNAME
GMAIL_APP_PASSWORD
PATENTFLOW_KIPRIS_SERVICE_KEY
```

`KUBE_CONFIG` must be base64-encoded:

```bash
base64 -i ~/.kube/config | pbcopy
```

Repository variables can override defaults:

```text
KUBE_NAMESPACE=patentflow
BE_INGRESS_HOST=api.patentflow.example.com
PATENTFLOW_CORS_ALLOWED_ORIGINS=https://patentflow.live
SPRING_PROFILES_ACTIVE=demo
SPRING_DATASOURCE_URL=jdbc:postgresql://patentflow-postgres:5432/patentflow?currentSchema=patentflow
SPRING_DATASOURCE_USERNAME=patentflow
PATENTFLOW_AGENT_URL=http://patentflow-agent:8000
```

## One-time cluster setup

If Harbor is private, the workflow creates or updates `harbor-regcred` in the namespace. The app secret is also applied from GitHub Secrets.

Before first deploy, make sure a PostgreSQL database is reachable from the cluster and that `SPRING_DATASOURCE_URL` points to it.
