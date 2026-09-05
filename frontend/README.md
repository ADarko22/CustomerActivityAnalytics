# Customer Activity Analytics - Frontend

Angular 22 + Angular Material single-page app: customer search, transaction/analytics views, and the AI
risk-assessment UI. See the [root README](../README.md) for the full architecture, design decisions, and how to
run the whole stack.

## Run standalone

```
npm start
```

Proxies `/api/**` to the backend via `proxy.conf.json` (`ng serve`, port `4200`). The backend (and Keycloak, for
login) must already be running — `../gradlew dev` from the repo root starts everything together.

## Tests & lint

```
npm test        # Karma/Jasmine unit tests, with coverage
npm run lint    # @angular-eslint + Prettier
```

## Build

```
npm run build
```

Produces the production bundle under `dist/`.
