package pl.com.bottega.factory.shortages.prediction.monitoring;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import pl.com.bottega.factory.product.management.RefNoId;
import pl.com.bottega.factory.shortages.prediction.Configuration;
import pl.com.bottega.factory.shortages.prediction.calculation.Forecasts;
import pl.com.bottega.factory.shortages.prediction.notification.NotificationOfShortage;
import pl.com.bottega.tools.TechnicalId;

import java.util.Optional;

@Component
@AllArgsConstructor
class ShortagePredictionProcessORMRepository implements ShortagePredictionProcessRepository {

    private final ShortagesDao dao;
    private final ShortageDiffPolicy policy = ShortageDiffPolicy.ValuesAreNotSame;
    private final Forecasts forecasts;
    private final Configuration configuration = () -> 14;
    private final NotificationOfShortage notifications;

    @Override
    public ShortagePredictionProcess get(RefNoId refNo) {
        Optional<ShortagesEntity> entity = dao.findByRefNo(refNo.getRefNo());
        return new ShortagePredictionProcess(
                entity.map(ShortagesEntity::createId)
                        .orElseGet(() -> ShortagesEntity.createId(refNo)),
                entity.map(ShortagesEntity::getShortages).orElse(null),
                policy, forecasts, configuration, new EventsHandler()
        );
    }

    @Override
    public void save(ShortagePredictionProcess model) {
        // persisted after event
    }

    private void save(NewShortage event) {
        RefNoId refNo = event.getRefNo();
        ShortagesEntity entity = TechnicalId.findOrDefault(
                refNo, dao::findOne,
                () -> dao.save(new ShortagesEntity(refNo.getRefNo())));
        entity.setShortages(event.getShortages());
        notifications.emit(event);
    }

    private void delete(ShortageSolved event) {
        dao.delete(TechnicalId.get(event.getRefNo()));
        notifications.emit(event);
    }

    private class EventsHandler implements ShortageEvents {
        @Override
        public void emit(NewShortage event) {
            save(event);
        }

        @Override
        public void emit(ShortageSolved event) {
            delete(event);
        }
    }
}
